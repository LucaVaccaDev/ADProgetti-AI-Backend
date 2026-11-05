package it.adprogetti.zio.edge.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * All SQL of the job engine. Claiming is serialized with a transaction-level advisory lock: one claim at a time
 * guarantees that a job is never taken twice and that two jobs with the same resource key never run together.
 */
@Repository
class JobRepository {

    private static final long CLAIM_LOCK = 0x5A494F4A4F42L; // "ZIOJOB"
    private static final String COLUMNS = """
            id, type, status, priority, created_at, started_at, finished_at, attempts, max_attempts, next_run_at,
            error_code, subject_ref, idempotency_key, resource_key, payload_json, result_json, cancel_requested,
            requested_by""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    JobRepository(JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    record NewJob(String type, int priority, int maxAttempts, Instant nextRunAt, String subjectRef,
            String idempotencyKey, String resourceKey, String payloadJson, String requestedBy) {
    }

    /** @return the id of the new job, or empty when a job with the same idempotency key already exists */
    Optional<UUID> insert(NewJob job) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                insert into job (id, type, status, priority, created_at, attempts, max_attempts, next_run_at,
                                 subject_ref, idempotency_key, resource_key, payload_json, requested_by)
                values (:id, :type, 'QUEUED', :priority, :now, 0, :max, :next, :subject, :key, :resource, :payload, :by)
                on conflict (idempotency_key) do nothing
                """).param("id", id).param("type", job.type()).param("priority", job.priority())
                .param("now", Timestamp.from(clock.instant())).param("max", job.maxAttempts())
                .param("next", Timestamp.from(job.nextRunAt())).param("subject", job.subjectRef())
                .param("key", job.idempotencyKey()).param("resource", job.resourceKey())
                .param("payload", job.payloadJson()).param("by", job.requestedBy()).update();
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    Optional<UUID> idByKey(String idempotencyKey) {
        return jdbc.sql("select id from job where idempotency_key = :k").param("k", idempotencyKey)
                .query(UUID.class).optional();
    }

    Optional<JobView> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from job where id = :id").param("id", id)
                .query(JobRepository::map).optional();
    }

    List<JobView> recent(int limit) {
        return jdbc.sql("select " + COLUMNS + " from job order by created_at desc limit :n").param("n", limit)
                .query(JobRepository::map).list();
    }

    /** Takes the most urgent runnable job and marks it RUNNING, or returns empty. */
    Optional<JobView> claimNext(Duration lease) {
        return tx.execute(status -> {
            jdbc.sql("select pg_advisory_xact_lock(:k)").param("k", CLAIM_LOCK).query().singleValue();
            Instant now = clock.instant();
            Optional<UUID> id = jdbc.sql("""
                    select j.id from job j
                    where j.status = 'QUEUED' and j.next_run_at <= :now
                      and (j.resource_key is null or not exists (
                            select 1 from job r where r.status = 'RUNNING' and r.resource_key = j.resource_key))
                    order by j.priority desc, j.next_run_at, j.created_at
                    limit 1
                    """).param("now", Timestamp.from(now)).query(UUID.class).optional();
            if (id.isEmpty()) {
                return Optional.<JobView>empty();
            }
            jdbc.sql("""
                    update job set status = 'RUNNING', attempts = attempts + 1,
                                   started_at = coalesce(started_at, :now), lease_until = :lease
                    where id = :id
                    """).param("now", Timestamp.from(now)).param("lease", Timestamp.from(now.plus(lease)))
                    .param("id", id.get()).update();
            return find(id.get());
        });
    }

    void renewLeases(List<UUID> ids, Duration lease) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("update job set lease_until = :l where status = 'RUNNING' and id in (:ids)")
                .param("l", Timestamp.from(clock.instant().plus(lease))).param("ids", ids).update();
    }

    void succeed(UUID id, String resultJson) {
        jdbc.sql("""
                update job set status = 'SUCCEEDED', finished_at = :now, result_json = :r, error_code = null,
                               lease_until = null
                where id = :id and status = 'RUNNING'
                """).param("now", Timestamp.from(clock.instant())).param("r", resultJson).param("id", id).update();
    }

    /** Back to the queue for another attempt. */
    void retryLater(UUID id, String errorCode, Instant nextRunAt) {
        jdbc.sql("""
                update job set status = 'QUEUED', error_code = :c, next_run_at = :n, lease_until = null
                where id = :id and status = 'RUNNING'
                """).param("c", errorCode).param("n", Timestamp.from(nextRunAt)).param("id", id).update();
    }

    void fail(UUID id, String errorCode) {
        jdbc.sql("""
                update job set status = 'FAILED', finished_at = :now, error_code = :c, lease_until = null
                where id = :id and status in ('RUNNING','QUEUED')
                """).param("now", Timestamp.from(clock.instant())).param("c", errorCode).param("id", id).update();
    }

    void markCancelled(UUID id) {
        jdbc.sql("""
                update job set status = 'CANCELLED', finished_at = :now, lease_until = null
                where id = :id and status in ('RUNNING','QUEUED')
                """).param("now", Timestamp.from(clock.instant())).param("id", id).update();
    }

    /** A queued job is cancelled at once; @return true if it was still queued. */
    boolean cancelQueued(UUID id) {
        return jdbc.sql("""
                update job set status = 'CANCELLED', finished_at = :now where id = :id and status = 'QUEUED'
                """).param("now", Timestamp.from(clock.instant())).param("id", id).update() == 1;
    }

    boolean requestCancel(UUID id) {
        return jdbc.sql("update job set cancel_requested = true where id = :id and status = 'RUNNING'")
                .param("id", id).update() == 1;
    }

    boolean cancelRequested(UUID id) {
        return jdbc.sql("select cancel_requested from job where id = :id").param("id", id)
                .query(Boolean.class).optional().orElse(false);
    }

    /** RUNNING jobs whose lease expired (or all of them at startup, when {@code onlyExpired} is false). */
    List<JobView> running(boolean onlyExpired) {
        if (onlyExpired) {
            return jdbc.sql("select " + COLUMNS + " from job where status = 'RUNNING' and lease_until < :now")
                    .param("now", Timestamp.from(clock.instant())).query(JobRepository::map).list();
        }
        return jdbc.sql("select " + COLUMNS + " from job where status = 'RUNNING'")
                .query(JobRepository::map).list();
    }

    int deleteFinishedBefore(Instant cutoff) {
        return jdbc.sql("delete from job where finished_at is not null and finished_at < :c")
                .param("c", Timestamp.from(cutoff)).update();
    }

    Optional<Instant> lastSlot(String schedule) {
        return jdbc.sql("select last_slot from schedule_state where name = :n").param("n", schedule)
                .query(Timestamp.class).optional().map(Timestamp::toInstant);
    }

    void saveSlot(String schedule, Instant slot) {
        jdbc.sql("""
                insert into schedule_state (name, last_slot) values (:n, :s)
                on conflict (name) do update set last_slot = excluded.last_slot
                """).param("n", schedule).param("s", Timestamp.from(slot)).update();
    }

    private static JobView map(ResultSet rs, int row) throws SQLException {
        return new JobView(rs.getObject("id", UUID.class), rs.getString("type"),
                JobStatus.valueOf(rs.getString("status")), rs.getInt("priority"), instant(rs, "created_at"),
                instant(rs, "started_at"), instant(rs, "finished_at"), rs.getInt("attempts"),
                rs.getInt("max_attempts"), instant(rs, "next_run_at"), rs.getString("error_code"),
                rs.getString("subject_ref"), rs.getString("idempotency_key"), rs.getString("resource_key"),
                rs.getString("payload_json"), rs.getString("result_json"), rs.getBoolean("cancel_requested"),
                rs.getString("requested_by"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
