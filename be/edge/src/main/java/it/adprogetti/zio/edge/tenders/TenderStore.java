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

    record SyncState(String status, Instant finishedAt, int fetched, int added, String errorCode) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    TenderStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true when the tender is new, false when an existing one was refreshed. */
    boolean upsert(TenderRow t) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.sql("""
                insert into tender (notice_number, title, buyer, amount, currency, deadline_at, published_on,
                    procedure_type, notice_type, cpv, place, lots, first_seen_at, last_seen_at)
                values (:n, :ti, :b, :a, :c, :d, :p, :pr, :nt, :cpv, :pl, :l, :now, :now)
                on conflict (notice_number) do update set title = :ti, buyer = :b, amount = :a, currency = :c,
                    deadline_at = :d, procedure_type = :pr, notice_type = :nt, cpv = :cpv, place = :pl, lots = :l,
                    last_seen_at = :now
                returning (xmax = 0)
                """).param("n", t.number()).param("ti", t.title()).param("b", t.buyer()).param("a", t.amount())
                .param("c", t.currency()).param("d", t.deadline() == null ? null : Timestamp.from(t.deadline()))
                .param("p", java.sql.Date.valueOf(t.published())).param("pr", t.procedure()).param("nt", t.noticeType())
                .param("cpv", String.join(" ", t.cpv())).param("pl", t.place()).param("l", t.lots()).param("now", now)
                .query(Boolean.class).single();
    }

    void startSync() {
        jdbc.sql("update tender_sync set last_started_at = :t, last_status = 'RUNNING', error_code = null where id = 1")
                .param("t", Timestamp.from(clock.instant())).update();
    }

    void finishSync(String status, int fetched, int added, String errorCode) {
        jdbc.sql("""
                update tender_sync set last_finished_at = :t, last_status = :s, fetched = :f, added = :a, error_code = :e
                where id = 1
                """).param("t", Timestamp.from(clock.instant())).param("s", status).param("f", fetched).param("a", added)
                .param("e", errorCode).update();
    }

    /** A sync left RUNNING by a crash is closed, so the Hub never shows a run that cannot finish. */
    void closeStaleSync() {
        jdbc.sql("update tender_sync set last_status = 'FAILED', error_code = 'INTERRUPTED', last_finished_at = :t "
                + "where last_status = 'RUNNING'").param("t", Timestamp.from(clock.instant())).update();
    }

    SyncState syncState() {
        return jdbc.sql("select last_status, last_finished_at, fetched, added, error_code from tender_sync where id = 1")
                .query((rs, i) -> new SyncState(rs.getString("last_status"),
                        rs.getTimestamp("last_finished_at") == null ? null : rs.getTimestamp("last_finished_at").toInstant(),
                        rs.getInt("fetched"), rs.getInt("added"), rs.getString("error_code"))).single();
    }

    int openCount() {
        return jdbc.sql("select count(*) from tender where deadline_at is null or deadline_at >= :now")
                .param("now", Timestamp.from(clock.instant())).query(Integer.class).single();
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
                        rs.getString("place"), rs.getInt("lots"))).list();
    }

    /** Old closed tenders are not kept for ever: public data can always be fetched again. */
    int purgeClosedBefore(Instant cutoff) {
        return jdbc.sql("delete from tender where deadline_at is not null and deadline_at < :c")
                .param("c", Timestamp.from(cutoff)).update();
    }
}
