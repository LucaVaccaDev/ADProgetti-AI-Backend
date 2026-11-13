package it.adprogetti.zio.edge.tenders;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Database access of the tenders. Only parameterised queries. */
@Repository
class TenderStore {

    static final String SOURCE_TED = "TED";
    static final String SOURCE_EC = "EUROPACONCORSI";

    record SyncState(String status, Instant finishedAt, int fetched, int added, String errorCode) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final it.adprogetti.zio.edge.docs.TenderDocumentLibrary library;

    TenderStore(JdbcClient jdbc, Clock clock, it.adprogetti.zio.edge.docs.TenderDocumentLibrary library) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.library = library;
    }

    /**
     * Stores a tender. The same tender often appears in two sources: a Europaconcorsi row that looks like one already
     * known from another source (same deadline day, similar title) is not added, and a new TED row replaces a similar
     * Europaconcorsi one (TED carries the amount). @return true when the tender is new, false when an existing one
     * was refreshed or the row was a duplicate.
     */
    boolean upsert(TenderRow t) {
        if (t.deadline() != null) {
            List<String[]> others = otherSources(t);
            for (String[] o : others) {
                if (TitleSimilarity.similar(t.title(), o[1])) {
                    if (SOURCE_EC.equals(t.source())) {
                        return false;
                    }
                    jdbc.sql("delete from tender where notice_number = :n").param("n", o[0]).update();
                }
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.sql("""
                insert into tender (notice_number, source, url, title, buyer, amount, currency, deadline_at, published_on,
                    procedure_type, notice_type, cpv, place, lots, first_seen_at, last_seen_at)
                values (:n, :src, :u, :ti, :b, :a, :c, :d, :p, :pr, :nt, :cpv, :pl, :l, :now, :now)
                on conflict (notice_number) do update set title = :ti, buyer = :b, amount = :a, currency = :c, url = :u,
                    deadline_at = :d, procedure_type = :pr, notice_type = :nt, cpv = :cpv, place = :pl, lots = :l,
                    last_seen_at = :now
                returning (xmax = 0)
                """).param("n", t.number()).param("src", t.source()).param("u", t.url()).param("ti", t.title())
                .param("b", t.buyer()).param("a", t.amount()).param("c", t.currency())
                .param("d", t.deadline() == null ? null : Timestamp.from(t.deadline()))
                .param("p", java.sql.Date.valueOf(t.published())).param("pr", t.procedure()).param("nt", t.noticeType())
                .param("cpv", String.join(" ", t.cpv())).param("pl", t.place()).param("l", t.lots()).param("now", now)
                .query(Boolean.class).single();
    }

    /** Number and title of the tenders from other sources whose deadline falls on the same day (Rome). */
    private List<String[]> otherSources(TenderRow t) {
        return jdbc.sql("""
                select notice_number, title from tender
                where source <> :src and (deadline_at at time zone 'Europe/Rome')::date = (:d at time zone 'Europe/Rome')::date
                """).param("src", t.source()).param("d", Timestamp.from(t.deadline()))
                .query((rs, i) -> new String[] { rs.getString(1), rs.getString(2) }).list();
    }

    private static int syncId(String source) {
        return SOURCE_TED.equals(source) ? 1 : 2;
    }

    void startSync(String source) {
        jdbc.sql("update tender_sync set last_started_at = :t, last_status = 'RUNNING', error_code = null where id = :i")
                .param("t", Timestamp.from(clock.instant())).param("i", syncId(source)).update();
    }

    void finishSync(String source, String status, int fetched, int added, String errorCode) {
        jdbc.sql("""
                update tender_sync set last_finished_at = :t, last_status = :s, fetched = :f, added = :a, error_code = :e
                where id = :i
                """).param("t", Timestamp.from(clock.instant())).param("s", status).param("f", fetched).param("a", added)
                .param("e", errorCode).param("i", syncId(source)).update();
    }

    /** A sync left RUNNING by a crash is closed, so the Hub never shows a run that cannot finish. */
    void closeStaleSync() {
        jdbc.sql("update tender_sync set last_status = 'FAILED', error_code = 'INTERRUPTED', last_finished_at = :t "
                + "where last_status = 'RUNNING'").param("t", Timestamp.from(clock.instant())).update();
    }

    SyncState syncState(String source) {
        return jdbc.sql("select last_status, last_finished_at, fetched, added, error_code from tender_sync where id = :i")
                .param("i", syncId(source)).query((rs, i) -> new SyncState(rs.getString("last_status"),
                        rs.getTimestamp("last_finished_at") == null ? null : rs.getTimestamp("last_finished_at").toInstant(),
                        rs.getInt("fetched"), rs.getInt("added"), rs.getString("error_code"))).single();
    }

    int openCount(String source) {
        return jdbc.sql("select count(*) from tender where source = :s and (deadline_at is null or deadline_at >= :now)")
                .param("s", source).param("now", Timestamp.from(clock.instant())).query(Integer.class).single();
    }

    /** Tenders still open, the soonest deadline first (no deadline last). */
    List<TenderRow> open(int limit) {
        return jdbc.sql("""
                select * from tender where deadline_at is null or deadline_at >= :now
                order by deadline_at asc nulls last, published_on desc, notice_number limit :l
                """).param("now", Timestamp.from(clock.instant())).param("l", limit)
                .query((rs, i) -> new TenderRow(rs.getString("notice_number"), rs.getString("title"), rs.getString("buyer"),
                        rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant(),
                        rs.getDate("published_on").toLocalDate(), rs.getString("procedure_type"), rs.getString("notice_type"),
                        rs.getString("cpv").isEmpty() ? List.of() : Arrays.asList(rs.getString("cpv").split(" ")),
                        rs.getString("place"), rs.getInt("lots"), rs.getString("source"), rs.getString("url"))).list();
    }

    /** Removes the rows of a source that a complete run did not see again. */
    int deleteUnseen(String source, Instant runStart) {
        return jdbc.sql("delete from tender where source = :s and last_seen_at < :t").param("s", source)
                .param("t", Timestamp.from(runStart)).update();
    }

    /**
     * Past the deadline a tender is deleted (public data can always be fetched again, and the studio asked for it), and
     * with it the documents kept for it (D-30): rows go by cascade, the files are removed by the library.
     */
    int purgeExpired() {
        int n = jdbc.sql("delete from tender where deadline_at is not null and deadline_at <= :now")
                .param("now", Timestamp.from(clock.instant())).update();
        library.purge();
        return n;
    }
}
