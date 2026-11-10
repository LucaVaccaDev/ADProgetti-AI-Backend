package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.edge.ai.AiGateway;
import it.adprogetti.zio.edge.ai.AiOutcome;
import it.adprogetti.zio.edge.ai.Priority;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.jobs.JobCancelledException;
import it.adprogetti.zio.edge.nas.NasException;
import it.adprogetti.zio.edge.nas.NasMode;
import it.adprogetti.zio.edge.nas.NasProperties;
import it.adprogetti.zio.edge.nas.NasReader;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scans the allowed NAS roots (read only, through {@link NasReader}), extracts the text of new or changed PDFs,
 * splits it into passages and stores them with their embeddings. Idempotent and resumable: an unchanged file is
 * skipped, a file whose content equals another is a DUPLICATE, a vanished file becomes REMOVED (only after a
 * complete pass), and every file is committed on its own, so an interruption loses at most the file in progress.
 * One bad file never stops the run; an unavailable NAS or embedding model does. Logs hold counts and codes only.
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);
    private static final int EXPECTED_DIMENSIONS = 1024;

    /** Counts of one run. {@code status}: COMPLETED, PARTIAL (file limit reached), FAILED. Cancelled runs throw. */
    public record RunResult(long runId, String status, String errorCode, int scanned, int indexed, int skipped,
            int noText, int failed, int duplicates, int tooLarge, int removed, int unsupported, int chunks) {
    }

    private static final class Abort extends RuntimeException {
        final String code;

        Abort(String code) {
            super(code, null, false, false);
            this.code = code;
        }
    }

    private final NasReader reader;
    private final NasProperties nas;
    private final DocsProperties props;
    private final PdfTextExtractor extractor;
    private final AiGateway ai;
    private final DocumentStore store;
    private final EdgeAudit audit;
    private final AtomicBoolean running = new AtomicBoolean();

    IndexingService(NasReader reader, NasProperties nas, DocsProperties props, PdfTextExtractor extractor,
            AiGateway ai, DocumentStore store, EdgeAudit audit) {
        this.reader = reader;
        this.nas = nas;
        this.props = props;
        this.extractor = extractor;
        this.ai = ai;
        this.store = store;
        this.audit = audit;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** @param checkpoint called between files; throws to stop the run (cancellation) */
    public RunResult run(Runnable checkpoint, String requestedBy) {
        if (!running.compareAndSet(false, true)) {
            return new RunResult(0, "ALREADY_RUNNING", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        long runId = 0;
        int[] n = new int[10]; // scanned, indexed, skipped, noText, failed, duplicates, tooLarge, removed, unsupported, chunks
        try {
            store.closeStaleRuns();
            runId = store.startRun();
            List<String> roots = roots();
            boolean limitReached = false;
            outer:
            for (String root : roots) {
                Deque<String> pending = new ArrayDeque<>(List.of(root));
                while (!pending.isEmpty()) {
                    checkpoint.run();
                    String dir = pending.pop();
                    List<NasReader.Entry> entries;
                    try {
                        entries = reader.list(NasMode.INDEX_READ, dir);
                    } catch (NasException e) {
                        if (e.code() == NasException.Code.UNAVAILABLE) {
                            throw new Abort("NAS_UNAVAILABLE");
                        }
                        log.warn("Folder skipped in indexing: {}", e.code());
                        continue; // too many entries, no permission, vanished: skip this folder only
                    }
                    List<String> subfolders = new ArrayList<>();
                    for (NasReader.Entry e : entries) {
                        String path = dir.endsWith("/") ? dir + e.name() : dir + "/" + e.name();
                        if (e.directory()) {
                            if (!ignoredFolder(e.name())) {
                                subfolders.add(path);
                            }
                            continue;
                        }
                        if (!isPdf(e.name())) {
                            n[8]++;
                            continue;
                        }
                        checkpoint.run();
                        if (props.maxFilesPerRun() > 0 && processed(n) >= props.maxFilesPerRun() && isNewOrChanged(path, e)) {
                            limitReached = true;
                            break outer;
                        }
                        n[0]++;
                        indexFile(path, e, runId, n);
                    }
                    for (int i = subfolders.size() - 1; i >= 0; i--) {
                        pending.push(subfolders.get(i)); // depth first, in name order
                    }
                }
            }
            if (!limitReached) {
                n[7] = store.markRemoved(roots, runId);
            }
            String status = limitReached ? "PARTIAL" : "COMPLETED";
            store.finishRun(runId, status, null, counts(n));
            audit.record("DOCS_INDEX_RUN", "run " + runId, EdgeAudit.SUCCESS, status + " " + summary(n)
                    + " by " + requestedBy);
            return result(runId, status, null, n);
        } catch (Abort a) {
            finish(runId, "FAILED", a.code, n, requestedBy);
            return result(runId, "FAILED", a.code, n);
        } catch (RuntimeException e) {
            // Cancellation and anything unexpected: record the run, then let the job engine decide.
            boolean cancelled = e instanceof JobCancelledException;
            finish(runId, cancelled ? "CANCELLED" : "FAILED", cancelled ? null : "UNEXPECTED", n, requestedBy);
            throw e;
        } finally {
            running.set(false);
        }
    }

    private void finish(long runId, String status, String code, int[] n, String requestedBy) {
        if (runId > 0) {
            store.finishRun(runId, status, code, counts(n));
            audit.record("DOCS_INDEX_RUN", "run " + runId, "COMPLETED".equals(status) ? EdgeAudit.SUCCESS
                    : EdgeAudit.FAILURE, status + (code == null ? "" : " " + code) + " " + summary(n) + " by " + requestedBy);
        }
    }

    private void indexFile(String path, NasReader.Entry entry, long runId, int[] n) {
        var known = store.find(path);
        if (known.isPresent() && !"REMOVED".equals(known.get().status()) && known.get().sizeBytes() == entry.size()
                && known.get().modifiedAt().toEpochMilli() == entry.modifiedAt().toEpochMilli()) {
            store.markSeen(known.get().id(), runId);
            n[2]++;
            return;
        }
        if (entry.size() > props.maxFileBytes()) {
            store.saveWithoutText(path, entry.size(), entry.modifiedAt(), null, "TOO_LARGE", "FILE_TOO_BIG", null, null,
                    null, runId);
            n[6]++;
            return;
        }
        PdfTextExtractor.Extracted extracted;
        try (InputStream in = reader.open(NasMode.INDEX_READ, path)) {
            extracted = extractor.extract(in);
        } catch (ExtractionException e) {
            boolean tooBig = e.code().equals("TOO_MANY_PAGES") || e.code().equals("TOO_MUCH_TEXT");
            store.saveWithoutText(path, entry.size(), entry.modifiedAt(), null, tooBig ? "TOO_LARGE" : "FAILED",
                    e.code(), null, null, null, runId);
            n[tooBig ? 6 : 4]++;
            return;
        } catch (NasException e) {
            if (e.code() == NasException.Code.UNAVAILABLE) {
                throw new Abort("NAS_UNAVAILABLE");
            }
            // vanished or unreadable right now: leave it for the next run
            return;
        } catch (java.io.IOException e) {
            throw new Abort("NAS_UNAVAILABLE");
        }
        var duplicate = store.indexedWithSameContent(extracted.sha256(), path);
        if (duplicate.isPresent()) {
            store.saveWithoutText(path, entry.size(), entry.modifiedAt(), extracted.sha256(), "DUPLICATE", null,
                    extracted.totalPages(), extracted.textChars(), duplicate.get(), runId);
            n[5]++;
            return;
        }
        if (PdfTextExtractor.looksScanned(extracted)) {
            store.saveWithoutText(path, entry.size(), entry.modifiedAt(), extracted.sha256(), "NO_TEXT", null,
                    extracted.totalPages(), extracted.textChars(), null, runId);
            n[3]++;
            return;
        }
        List<Chunker.Chunk> chunks = Chunker.split(extracted.pages(), props.chunkSize(), props.chunkOverlap());
        if (chunks.isEmpty()) {
            store.saveWithoutText(path, entry.size(), entry.modifiedAt(), extracted.sha256(), "NO_TEXT", null,
                    extracted.totalPages(), extracted.textChars(), null, runId);
            n[3]++;
            return;
        }
        float[][] vectors = embed(chunks);
        store.saveIndexed(path, entry.size(), entry.modifiedAt(), extracted.sha256(), extracted.totalPages(),
                extracted.textChars(), chunks, vectors, runId);
        n[1]++;
        n[9] += chunks.size();
    }

    private float[][] embed(List<Chunker.Chunk> chunks) {
        float[][] all = new float[chunks.size()][];
        for (int from = 0; from < chunks.size(); from += props.embedBatch()) {
            int to = Math.min(from + props.embedBatch(), chunks.size());
            List<String> texts = chunks.subList(from, to).stream().map(Chunker.Chunk::content).toList();
            AiOutcome<float[][]> out = ai.embed(texts, Priority.LOW, "docs-index");
            if (!out.ok() || out.value().length != texts.size()) {
                throw new Abort("AI_UNAVAILABLE");
            }
            for (int i = 0; i < texts.size(); i++) {
                if (out.value()[i].length != EXPECTED_DIMENSIONS) {
                    throw new Abort("EMBEDDING_DIMENSIONS");
                }
                all[from + i] = out.value()[i];
            }
        }
        return all;
    }

    private boolean isNewOrChanged(String path, NasReader.Entry e) {
        return store.find(path).map(k -> "REMOVED".equals(k.status()) || k.sizeBytes() != e.size()
                || k.modifiedAt().toEpochMilli() != e.modifiedAt().toEpochMilli()).orElse(true);
    }

    private List<String> roots() {
        List<String> configured = props.indexRoots();
        return configured.isEmpty() ? nas.modes().getOrDefault(NasMode.INDEX_READ, List.of()) : configured;
    }

    private static boolean isPdf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") && !lower.startsWith("~$") && !lower.startsWith(".");
    }

    private static boolean ignoredFolder(String name) {
        return name.startsWith(".") || name.equals("#recycle") || name.equals("@eaDir");
    }

    private static int processed(int[] n) {
        return n[1] + n[3] + n[4] + n[5] + n[6];
    }

    private static DocumentStore.Counts counts(int[] n) {
        return new DocumentStore.Counts(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9]);
    }

    private static RunResult result(long runId, String status, String code, int[] n) {
        return new RunResult(runId, status, code, n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9]);
    }

    private static String summary(int[] n) {
        return "scanned=" + n[0] + " indexed=" + n[1] + " skipped=" + n[2] + " noText=" + n[3] + " failed=" + n[4]
                + " duplicates=" + n[5] + " tooLarge=" + n[6] + " removed=" + n[7] + " chunks=" + n[9];
    }
}
