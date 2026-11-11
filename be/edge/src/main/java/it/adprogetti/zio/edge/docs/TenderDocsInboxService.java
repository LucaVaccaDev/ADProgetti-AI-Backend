package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the tender documents the user dropped in the inbox, then deletes them: a document is read once and never kept
 * (the committente's rule, 11/11/2026). Files can sit loose in the inbox: the tender is found from a tender number in the
 * file name or from the words of its title in the first pages ({@link TenderMatcher}); a file that cannot be assigned
 * with certainty stays where it is and is reported. A sub-folder named after a tender number still works and decides the
 * tender by itself. A file is accepted only if it is a regular file (no links), within the size cap, and a real PDF or a
 * signed envelope by its first bytes (not by its name); files touched in the last 15 seconds are left alone (still
 * copying). A file the AI could not read yet because the provider was down waits for another try until
 * {@code keepUnread} passes. Documents of the same tender are merged into one result in {@code tender_analysis}; the
 * audit and the job result hold counts only.
 */
@Service
public class TenderDocsInboxService {

    public record Result(int tenders, int files, int analysed, int incomplete, int failed, int rejected, int waiting,
            int unmatched) {
    }

    private static final Pattern NOTICE = Pattern.compile("[A-Za-z0-9._-]{1,20}");

    private final TenderDocsProperties props;
    private final TenderDocumentAnalyzer analyzer;
    private final TenderMatcher matcher;
    private final JdbcClient jdbc;
    private final EdgeAudit audit;
    private final Clock clock;

    TenderDocsInboxService(TenderDocsProperties props, TenderDocumentAnalyzer analyzer, TenderMatcher matcher,
            JdbcClient jdbc, EdgeAudit audit, Clock clock) {
        this.props = props;
        this.analyzer = analyzer;
        this.matcher = matcher;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** What has been read for one tender in this run. */
    private static final class Acc {
        final List<TenderRequirements> parts = new ArrayList<>();
        String sha;
        String model;
        int pages;
        boolean incomplete;
        boolean failed;
        String errorCode;
    }

    private record Item(Path file, String notice) {
    }

    private static final class Counts {
        int files, analysed, incomplete, failed, rejected, waiting, unmatched, duplicates;
        final java.util.Set<String> seen = new java.util.HashSet<>();
    }

    public Result run(Runnable checkpoint, String requestedBy) {
        Counts n = new Counts();
        Map<String, Acc> byTender = new LinkedHashMap<>();
        if (Files.isDirectory(props.inbox(), LinkOption.NOFOLLOW_LINKS)) {
            for (Item item : items()) {
                checkpoint.run();
                process(item, byTender, n, requestedBy);
            }
        }
        for (var e : byTender.entrySet()) {
            Acc a = e.getValue();
            if (!a.parts.isEmpty() || a.failed) {
                String status = a.parts.isEmpty() ? "FALLITA" : a.incomplete || a.failed ? "INCOMPLETA" : "ANALIZZATA";
                store(e.getKey(), status, a, requestedBy);
            }
        }
        cleanEmptyFolders();
        Result result = new Result(byTender.size(), n.files, n.analysed, n.incomplete, n.failed, n.rejected, n.waiting,
                n.unmatched);
        audit.record("TENDER_DOCS_READ", "inbox", EdgeAudit.SUCCESS, "by " + requestedBy + ": " + result.tenders()
                + " tenders, " + n.files + " files, " + n.analysed + " read, " + n.incomplete + " incomplete, " + n.failed
                + " failed, " + n.rejected + " rejected, " + n.waiting + " waiting, " + n.unmatched + " unassigned");
        return result;
    }

    private void process(Item item, Map<String, Acc> byTender, Counts n, String requestedBy) {
        Path file = item.file();
        if (justWritten(file)) {
            n.waiting++; // still being copied: not read, not deleted
            return;
        }
        n.files++;
        if (!acceptable(file)) {
            n.rejected++;
            delete(file); // not a PDF, too big or a link: removed, never read
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            n.rejected++;
            delete(file);
            return;
        }
        if (!P7mUnwrapper.isPdf(bytes)) {
            // a digitally signed envelope (.p7m): only the enclosed PDF is read, signatures are not checked
            bytes = P7mUnwrapper.unwrap(bytes).filter(P7mUnwrapper::isPdf).orElse(null);
        }
        if (bytes != null && !n.seen.add(sha(bytes))) {
            n.duplicates++; // the same document twice in one run (e.g. "file (1).pdf"): read once, the copy just goes
            delete(file);
            return;
        }
        TenderDocumentAnalyzer.Result r;
        String notice = item.notice();
        if (bytes == null) {
            r = TenderDocumentAnalyzer.Result.failed("NOT_A_PDF", null, 0);
        } else {
            var read = analyzer.read(new ByteArrayInputStream(bytes));
            if (read.doc() == null) {
                r = TenderDocumentAnalyzer.Result.failed(read.errorCode(), null, 0);
            } else {
                if (notice == null) {
                    notice = matcher.fromFileName(file.getFileName().toString())
                            .or(() -> matcher.fromContent(read.doc().pages())).orElse(null);
                }
                if (notice == null) {
                    n.unmatched++; // never guessed onto a tender: it stays, the user renames it with the number
                    if (tooOld(file)) {
                        delete(file);
                    }
                    return;
                }
                r = analyzer.analyze(read.doc(), requestedBy);
            }
        }
        boolean transientError = "FALLITA".equals(r.status()) && r.errorCode() != null && r.errorCode().startsWith("AI_");
        if (transientError && !tooOld(file)) {
            n.waiting++; // the provider did not answer: the file waits for the next run
            return;
        }
        if (notice == null) { // unreadable PDF with no tender: nothing to store, the file goes
            n.failed++;
            delete(file);
            return;
        }
        Acc a = byTender.computeIfAbsent(notice, k -> new Acc());
        a.sha = a.sha == null ? r.sha256() : a.sha;
        a.pages += r.pages();
        a.model = r.model() != null ? r.model() : a.model;
        if (r.requirements() != null) {
            a.parts.add(r.requirements());
        }
        if ("INCOMPLETA".equals(r.status())) {
            n.incomplete++;
            a.incomplete = true;
            a.errorCode = r.errorCode();
        } else if ("FALLITA".equals(r.status())) {
            n.failed++;
            a.failed = true;
            a.errorCode = r.errorCode();
        } else {
            n.analysed++;
        }
        delete(file); // read once, then gone
    }

    /** Files in known tender sub-folders (the folder decides), then the loose files of the inbox. */
    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        List<Path> loose = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(props.inbox())) {
            List<Path> entries = new ArrayList<>();
            ds.forEach(entries::add);
            entries.sort(null);
            for (Path p : entries) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    if (NOTICE.matcher(name).matches() && tenderExists(name)) {
                        files(p).forEach(f -> out.add(new Item(f, name)));
                    }
                } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    loose.add(p);
                }
            }
        } catch (IOException e) {
            // an unreadable inbox is an empty one for this run
        }
        loose.forEach(f -> out.add(new Item(f, null)));
        return out;
    }

    private List<Path> files(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (!p.getFileName().toString().startsWith(".") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            // see above
        }
        out.sort(null);
        return out;
    }

    private boolean acceptable(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > props.maxPdfBytes()
                    || Files.size(file) < 100) {
                return false;
            }
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] head = in.readNBytes(5);
                boolean pdf = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-");
                boolean envelope = head.length >= 2 && head[0] == 0x30 && (head[1] & 0x80) != 0
                        && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".p7m");
                return pdf || envelope; // by the first bytes, not only by the name
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** A file touched in the last 15 seconds may still be copying. */
    private boolean justWritten(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now(clock).minusSeconds(15));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean tooOld(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(Instant.now(clock).minus(props.keepUnread()));
        } catch (IOException e) {
            return true;
        }
    }

    private boolean tenderExists(String notice) {
        return jdbc.sql("select count(*) from tender where notice_number = :n").param("n", notice).query(Integer.class)
                .single() > 0;
    }

    private void store(String notice, String status, Acc a, String requestedBy) {
        TenderRequirements req = a.parts.isEmpty() ? null : TenderRequirements.merge(a.parts);
        jdbc.sql("""
                insert into tender_analysis (notice_number, status, source, doc_sha256, pages, result_json, model,
                    error_code, requested_by, analysed_at)
                values (:n, :s, 'CARICATO', :h, :p, :j, :m, :e, :u, :t)
                on conflict (notice_number) do update set status = :s, source = 'CARICATO', doc_sha256 = :h, pages = :p,
                    result_json = :j, model = :m, error_code = :e, requested_by = :u, analysed_at = :t""")
                .param("n", notice).param("s", status).param("h", a.sha).param("p", a.pages)
                .param("j", req == null ? null : Payloads.write(req)).param("m", a.model).param("e", a.errorCode)
                .param("u", requestedBy).param("t", Timestamp.from(clock.instant())).update();
    }

    private static String sha(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // a file that cannot be deleted is read again on the next run, never kept silently
        }
    }

    private void cleanEmptyFolders() {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(props.inbox())) {
            for (Path p : ds) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    try (DirectoryStream<Path> inner = Files.newDirectoryStream(p)) {
                        if (!inner.iterator().hasNext()) {
                            Files.deleteIfExists(p);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // left as it is
        }
    }
}
