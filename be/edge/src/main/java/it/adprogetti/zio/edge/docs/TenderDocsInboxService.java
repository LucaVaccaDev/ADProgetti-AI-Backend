package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
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
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the tender documents the user dropped in the inbox folder, one sub-folder per tender, then deletes them: the
 * document is read once and never kept (the committente's rule, 11/11/2026). A file is accepted only if it is a regular
 * file (no links), within the size cap, and really a PDF by its first bytes (not by its name). A file the AI could not
 * read yet because the provider was down stays for another try until {@code keepUnread} passes. The result of a tender
 * is stored in {@code tender_analysis}; the audit and the job result hold counts only.
 */
@Service
public class TenderDocsInboxService {

    public record Result(int tenders, int files, int analysed, int incomplete, int failed, int rejected, int waiting) {
    }

    private static final Pattern NOTICE = Pattern.compile("[A-Za-z0-9._-]{1,20}");

    private final TenderDocsProperties props;
    private final TenderDocumentAnalyzer analyzer;
    private final JdbcClient jdbc;
    private final EdgeAudit audit;
    private final Clock clock;

    TenderDocsInboxService(TenderDocsProperties props, TenderDocumentAnalyzer analyzer, JdbcClient jdbc, EdgeAudit audit,
            Clock clock) {
        this.props = props;
        this.analyzer = analyzer;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    public Result run(Runnable checkpoint, String requestedBy) {
        int tenders = 0, files = 0, analysed = 0, incomplete = 0, failed = 0, rejected = 0, waiting = 0;
        if (!Files.isDirectory(props.inbox(), LinkOption.NOFOLLOW_LINKS)) {
            return new Result(0, 0, 0, 0, 0, 0, 0);
        }
        for (Path dir : subDirectories()) {
            checkpoint.run();
            String notice = dir.getFileName().toString();
            if (!NOTICE.matcher(notice).matches() || !tenderExists(notice)) {
                continue; // not a known tender: left alone, never read
            }
            tenders++;
            List<TenderRequirements> parts = new ArrayList<>();
            String firstSha = null;
            String model = null;
            int pages = 0;
            boolean anyIncomplete = false, anyFailed = false;
            String errorCode = null;
            for (Path file : pdfFiles(dir)) {
                checkpoint.run();
                if (justWritten(file)) {
                    waiting++; // still being copied: not read, not deleted
                    continue;
                }
                files++;
                if (!acceptable(file)) {
                    rejected++;
                    delete(file); // not a PDF, too big or a link: removed, never read
                    continue;
                }
                TenderDocumentAnalyzer.Result r;
                try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    r = analyzer.analyze(in, requestedBy);
                } catch (IOException e) {
                    r = TenderDocumentAnalyzer.Result.failed("UNREADABLE", null, 0);
                }
                boolean transientError = "FALLITA".equals(r.status())
                        && ("AI_UNAVAILABLE".equals(r.errorCode()) || r.errorCode() != null && r.errorCode().startsWith("AI_"));
                if (transientError && !tooOld(file)) {
                    waiting++; // the provider did not answer: the file waits for the next run
                    continue;
                }
                firstSha = firstSha == null ? r.sha256() : firstSha;
                pages += r.pages();
                model = r.model() != null ? r.model() : model;
                if (r.requirements() != null) {
                    parts.add(r.requirements());
                }
                if ("INCOMPLETA".equals(r.status())) {
                    incomplete++;
                    anyIncomplete = true;
                    errorCode = r.errorCode();
                } else if ("FALLITA".equals(r.status())) {
                    failed++;
                    anyFailed = true;
                    errorCode = r.errorCode();
                } else {
                    analysed++;
                }
                delete(file); // read once, then gone
            }
            if (!parts.isEmpty() || anyFailed) {
                String status = parts.isEmpty() ? "FALLITA" : anyIncomplete || anyFailed ? "INCOMPLETA" : "ANALIZZATA";
                store(notice, status, firstSha, pages, parts.isEmpty() ? null : TenderRequirements.merge(parts), model,
                        errorCode, requestedBy);
            }
            deleteIfEmpty(dir);
        }
        Result result = new Result(tenders, files, analysed, incomplete, failed, rejected, waiting);
        audit.record("TENDER_DOCS_READ", "inbox", EdgeAudit.SUCCESS, "by " + requestedBy + ": " + tenders + " tenders, "
                + files + " files, " + analysed + " read, " + incomplete + " incomplete, " + failed + " failed, "
                + rejected + " rejected, " + waiting + " waiting");
        return result;
    }

    private boolean acceptable(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > props.maxPdfBytes()
                    || Files.size(file) < 100) {
                return false;
            }
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                return new String(in.readNBytes(5), java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-");
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

    private void store(String notice, String status, String sha, int pages, TenderRequirements req, String model,
            String errorCode, String requestedBy) {
        jdbc.sql("""
                insert into tender_analysis (notice_number, status, source, doc_sha256, pages, result_json, model,
                    error_code, requested_by, analysed_at)
                values (:n, :s, 'CARICATO', :h, :p, :j, :m, :e, :u, :t)
                on conflict (notice_number) do update set status = :s, source = 'CARICATO', doc_sha256 = :h, pages = :p,
                    result_json = :j, model = :m, error_code = :e, requested_by = :u, analysed_at = :t""")
                .param("n", notice).param("s", status).param("h", sha).param("p", pages)
                .param("j", req == null ? null : Payloads.write(req)).param("m", model).param("e", errorCode)
                .param("u", requestedBy).param("t", Timestamp.from(clock.instant())).update();
    }

    private List<Path> subDirectories() {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(props.inbox())) {
            for (Path p : ds) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            // an unreadable inbox is an empty one for this run
        }
        out.sort(null);
        return out;
    }

    private List<Path> pdfFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!name.startsWith(".") && (name.endsWith(".pdf") || Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            // see above
        }
        out.sort(null);
        return out;
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // a file that cannot be deleted is reported by the next run as already read (same hash), never kept silently
        }
    }

    private void deleteIfEmpty(Path dir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            if (!ds.iterator().hasNext()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            // left as it is
        }
    }
}
