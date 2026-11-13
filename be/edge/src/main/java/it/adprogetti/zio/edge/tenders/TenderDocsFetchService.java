package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.docs.TenderDocsInboxService;
import it.adprogetti.zio.edge.docs.TenderDocsProperties;
import it.adprogetti.zio.edge.docs.TenderDocumentLibrary;
import it.adprogetti.zio.edge.jobs.JobCancelledException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Fetches, on its own, the documents of the open tenders and reads them (D-30). The public documents are kept in the
 * library until the tender expires. Today one adapter exists: the files attached to a tender page on Europaconcorsi
 * (read with the studio's account, a request every few seconds). A tender whose documents cannot be fetched is marked
 * {@code NON_SCARICABILE} (the card then offers the upload), and a file above the cap {@code TROPPO_GRANDE}. Nothing is
 * guessed: an attachment that is not a PDF or a signed PDF is skipped, never stored.
 */
@Service
public class TenderDocsFetchService {

    public record Result(int tenders, int fetched, int documents, int notAvailable, int tooLarge, int analysed, int waiting,
            int purged) {
    }

    private static final Logger log = LoggerFactory.getLogger(TenderDocsFetchService.class);

    private final TenderDocsProperties props;
    private final EuropaconcorsiProperties ecProps;
    private final EuropaconcorsiClient client;
    private final TenderDocumentLibrary library;
    private final TenderDocsInboxService reader;
    private final TendersProjector projector;
    private final JdbcClient jdbc;
    private final EdgeAudit audit;
    private final Clock clock;

    TenderDocsFetchService(TenderDocsProperties props, EuropaconcorsiProperties ecProps, EuropaconcorsiClient client,
            TenderDocumentLibrary library, TenderDocsInboxService reader, TendersProjector projector, JdbcClient jdbc,
            EdgeAudit audit, Clock clock) {
        this.props = props;
        this.ecProps = ecProps;
        this.client = client;
        this.library = library;
        this.reader = reader;
        this.projector = projector;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** @param checkpoint called between tenders: it throws when the job was cancelled */
    public Result run(Runnable checkpoint, String requestedBy) throws TedUnavailableException {
        int purged = library.purge();
        int tenders = 0, fetched = 0, documents = 0, notAvailable = 0, tooLarge = 0, analysed = 0, waiting = 0;
        List<String> toFetch = ecProps.enabled() && client.hasCredentials() ? candidates() : List.of();
        if (!toFetch.isEmpty()) {
            var session = client.login();
            boolean relogged = false;
            for (String notice : toFetch) {
                checkpoint.run();
                pause();
                tenders++;
                Outcome o;
                try {
                    o = fetchOne(session, notice);
                } catch (TedUnavailableException e) {
                    // The portal may end a session in the middle of a run: log in once more and retry this tender; a
                    // second loss stops the run (never hammer the portal, never loop on a refused login).
                    if (!"EC_LOGIN_FAILED".equals(e.code()) || relogged) {
                        throw "EC_LOGIN_FAILED".equals(e.code()) ? new TedUnavailableException("EC_SESSION_LOST", false) : e;
                    }
                    relogged = true;
                    pause();
                    session = client.login();
                    try {
                        o = fetchOne(session, notice);
                    } catch (TedUnavailableException again) {
                        throw "EC_LOGIN_FAILED".equals(again.code()) ? new TedUnavailableException("EC_SESSION_LOST", false) : again;
                    }
                }
                fetched += o.documents > 0 ? 1 : 0;
                documents += o.documents;
                notAvailable += o.notAvailable ? 1 : 0;
                tooLarge += o.tooLarge ? 1 : 0;
            }
        }
        // Documents kept but not read yet (the AI was down, or they were just stored): read them now.
        for (String notice : unread()) {
            checkpoint.run();
            String status = reader.analyzeStored(notice, requestedBy);
            if ("IN_ATTESA".equals(status)) {
                waiting++;
            } else {
                analysed++;
            }
        }
        projector.publishSoon();
        Result result = new Result(tenders, fetched, documents, notAvailable, tooLarge, analysed, waiting, purged);
        audit.record("TENDER_DOCS_FETCH", "europaconcorsi", EdgeAudit.SUCCESS, "by " + requestedBy + ": " + tenders
                + " tenders, " + documents + " documents, " + notAvailable + " not available, " + tooLarge + " too large, "
                + analysed + " read, " + waiting + " waiting");
        log.info("Tender documents: {} tenders, {} documents, {} not available", tenders, documents, notAvailable);
        return result;
    }

    private static final class Outcome {
        int documents;
        boolean notAvailable;
        boolean tooLarge;
    }

    private Outcome fetchOne(EuropaconcorsiClient.Session session, String notice) throws TedUnavailableException {
        Outcome out = new Outcome();
        String id = notice.substring("EC-".length());
        String page;
        try {
            page = session.tenderPage(id);
        } catch (TedUnavailableException e) {
            if (!e.retryable()) {
                throw e; // a refused login or block stops the run: never hammer the portal
            }
            mark(notice, "NON_SCARICABILE", e.code());
            out.notAvailable = true;
            return out;
        }
        var attachments = EuropaconcorsiParser.attachments(page, client.attachmentHosts());
        if (attachments.isEmpty()) {
            mark(notice, "NON_SCARICABILE", "NO_ATTACHMENTS");
            out.notAvailable = true;
            return out;
        }
        String problem = null;
        for (var a : attachments) {
            pause();
            var d = session.download(a.url(), library.maxBytes());
            if (d.tooLarge()) {
                out.tooLarge = true;
            } else if (d.bytes() == null) {
                problem = d.error();
            } else {
                byte[] pdf = TenderDocumentLibrary.asPdf(d.bytes()).orElse(null);
                if (pdf == null) {
                    problem = "NOT_A_PDF"; // a zip, a spreadsheet, a drawing: not read, not stored
                } else if (library.store(notice, nameOf(a), pdf, "SCARICATO", a.url()).isPresent()) {
                    out.documents++;
                }
            }
        }
        if (out.documents > 0) {
            clearMark(notice);
        } else if (out.tooLarge) {
            mark(notice, "TROPPO_GRANDE", null);
        } else {
            mark(notice, "NON_SCARICABILE", problem == null ? "NO_NEW_DOCUMENT" : problem);
            out.notAvailable = true;
        }
        return out;
    }

    private static String nameOf(EuropaconcorsiParser.Attachment a) {
        String last = a.url().substring(a.url().lastIndexOf('/') + 1);
        String label = a.label() == null ? "documento" : a.label();
        return label.equals("documento") || label.length() < 3 ? last : label + ".pdf";
    }

    /** Open tenders of Europaconcorsi with no document kept and not recently tried (or never). */
    private List<String> candidates() {
        return jdbc.sql("""
                select t.notice_number from tender t
                where t.source = 'EUROPACONCORSI' and t.notice_number like 'EC-%'
                  and (t.deadline_at is null or t.deadline_at > :now)
                  and not exists (select 1 from tender_document d where d.notice_number = t.notice_number)
                  and (t.doc_status is null or (t.doc_status = 'NON_SCARICABILE' and t.doc_checked_at < :retry))
                order by t.deadline_at nulls last, t.notice_number limit :max""")
                .param("now", Timestamp.from(clock.instant()))
                .param("retry", Timestamp.from(clock.instant().minus(java.time.Duration.ofHours(24))))
                .param("max", props.fetchMaxPerRun()).query(String.class).list();
    }

    /** Tenders with documents kept and no result, or a result the AI could not produce. */
    private List<String> unread() {
        return jdbc.sql("""
                select distinct d.notice_number from tender_document d
                left join tender_analysis a on a.notice_number = d.notice_number
                where a.notice_number is null or (a.status = 'FALLITA' and a.error_code like 'AI\\_%')
                order by d.notice_number""").query(String.class).list();
    }

    private void mark(String notice, String status, String note) {
        jdbc.sql("update tender set doc_status = :s, doc_note = :n, doc_checked_at = :t where notice_number = :id")
                .param("s", status).param("n", note).param("t", Timestamp.from(clock.instant())).param("id", notice).update();
    }

    private void clearMark(String notice) {
        jdbc.sql("update tender set doc_status = null, doc_note = null, doc_checked_at = :t where notice_number = :id")
                .param("t", Timestamp.from(clock.instant())).param("id", notice).update();
    }

    private void pause() {
        try {
            Thread.sleep(props.fetchPause().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobCancelledException();
        }
    }
}
