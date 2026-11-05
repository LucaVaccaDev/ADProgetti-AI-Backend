package it.adprogetti.zio.edge.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import it.adprogetti.zio.edge.support.TestJobHandlers;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class JobEngineTest extends EdgeIntegrationTestBase {

    @Autowired JobService jobs;
    @Autowired JobEngine engine;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        TestJobHandlers.reset();
    }

    private static String key() {
        return "test:" + UUID.randomUUID();
    }

    private JobService.Request request(String type, String payload, String resource, int priority, Duration delay) {
        return new JobService.Request(type, payload, null, key(), resource, priority, delay, "tester");
    }

    private UUID enqueue(String type) {
        return jobs.enqueue(request(type, null, null, 5, null)).id();
    }

    private JobView await(UUID id, JobStatus status) {
        waitFor(() -> jobs.find(id).map(j -> j.status() == status).orElse(false), 10);
        return jobs.find(id).orElseThrow();
    }

    static void waitFor(BooleanSupplier condition, int seconds) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("condition not met within " + seconds + " s");
    }

    @Test
    void a_job_runs_and_keeps_its_result() {
        UUID id = enqueue("TEST_OK");
        JobView done = await(id, JobStatus.SUCCEEDED);
        assertThat(done.attempts()).isEqualTo(1);
        assertThat(done.resultJson()).isEqualTo("{\"ok\":true}");
        assertThat(done.startedAt()).isNotNull();
        assertThat(done.finishedAt()).isNotNull();
    }

    @Test
    void the_same_idempotency_key_never_creates_a_second_job() {
        JobService.Request first = request("TEST_OK", null, null, 5, null);
        JobService.Enqueued a = jobs.enqueue(first);
        JobService.Enqueued b = jobs.enqueue(first);
        assertThat(a.created()).isTrue();
        assertThat(b.created()).isFalse();
        assertThat(b.id()).isEqualTo(a.id());
        await(a.id(), JobStatus.SUCCEEDED);
        assertThat(TestJobHandlers.RUNS.get(a.id()).get()).isEqualTo(1);
    }

    @Test
    void fifty_jobs_on_three_workers_each_run_exactly_once() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ids.add(enqueue("TEST_OK"));
        }
        ids.forEach(id -> await(id, JobStatus.SUCCEEDED));
        assertThat(ids).allSatisfy(id -> assertThat(TestJobHandlers.RUNS.get(id).get()).isEqualTo(1));
    }

    @Test
    void a_transient_failure_is_retried_until_it_succeeds() {
        UUID id = jobs.enqueue(request("TEST_FLAKY", "{\"succeedAt\":3}", null, 5, null)).id();
        JobView done = await(id, JobStatus.SUCCEEDED);
        assertThat(done.attempts()).isEqualTo(3);
        assertThat(done.errorCode()).isNull();
    }

    @Test
    void a_permanent_failure_is_not_retried() {
        UUID id = enqueue("TEST_PERMANENT");
        JobView failed = await(id, JobStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(1);
        assertThat(failed.errorCode()).isEqualTo("BAD_INPUT");
    }

    @Test
    void retries_stop_at_the_maximum_and_the_error_text_is_never_stored() {
        UUID id = enqueue("TEST_ALWAYS_FAILS");
        JobView failed = await(id, JobStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(3);
        assertThat(failed.errorCode()).isEqualTo("EXECUTION_ERROR");
        assertThat(jdbc.queryForObject("select count(*) from job where id = ? and error_code like '%secret%'",
                Integer.class, id)).isZero();
    }

    @Test
    void jobs_sharing_a_resource_key_never_run_together() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(jobs.enqueue(request("TEST_EXCLUSIVE", null, "phase:42", 5, null)).id());
        }
        ids.forEach(id -> await(id, JobStatus.SUCCEEDED));
        assertThat(TestJobHandlers.MAX_CONCURRENT.get()).isEqualTo(1);
    }

    @Test
    void jobs_without_a_resource_key_do_run_in_parallel() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(enqueue("TEST_EXCLUSIVE"));
        }
        ids.forEach(id -> await(id, JobStatus.SUCCEEDED));
        assertThat(TestJobHandlers.MAX_CONCURRENT.get()).isGreaterThan(1);
    }

    @Test
    void the_most_urgent_job_goes_first() {
        engine.pause();
        try {
            sleep(200); // let in-flight claims finish
            UUID low = jobs.enqueue(request("TEST_OK", null, "prio", 1, null)).id();
            UUID high = jobs.enqueue(request("TEST_OK", null, "prio", 9, null)).id();
            UUID mid = jobs.enqueue(request("TEST_OK", null, "prio", 5, null)).id();
            List<UUID> order = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                JobView claimed = repository().claimNext(Duration.ofSeconds(30)).orElseThrow();
                order.add(claimed.id());
                repository().succeed(claimed.id(), null);
            }
            assertThat(order).containsExactly(high, mid, low);
        } finally {
            engine.resume();
        }
    }

    @Autowired JobRepository repo;

    private JobRepository repository() {
        return repo;
    }

    @Test
    void a_queued_job_is_cancelled_at_once() {
        UUID id = jobs.enqueue(request("TEST_OK", null, null, 5, Duration.ofMinutes(10))).id();
        assertThat(jobs.cancel(id, "tester")).isEqualTo(JobService.CancelOutcome.CANCELLED);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(jobs.cancel(id, "tester")).isEqualTo(JobService.CancelOutcome.ALREADY_FINISHED);
    }

    @Test
    void a_running_cancellable_job_stops_at_its_next_safe_point() throws Exception {
        UUID id = enqueue("TEST_CANCELLABLE");
        assertThat(TestJobHandlers.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(jobs.cancel(id, "tester")).isEqualTo(JobService.CancelOutcome.CANCEL_REQUESTED);
        assertThat(await(id, JobStatus.CANCELLED).finishedAt()).isNotNull();
    }

    @Test
    void a_running_job_that_cannot_be_cancelled_keeps_running() throws Exception {
        UUID id = enqueue("TEST_BLOCKING");
        assertThat(TestJobHandlers.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(jobs.cancel(id, "tester")).isEqualTo(JobService.CancelOutcome.NOT_CANCELLABLE);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.RUNNING);
        TestJobHandlers.release.countDown();
        await(id, JobStatus.SUCCEEDED);
    }

    @Test
    void cancelling_an_unknown_job_says_so() {
        assertThat(jobs.cancel(UUID.randomUUID(), "tester")).isEqualTo(JobService.CancelOutcome.NOT_FOUND);
    }

    @Test
    void a_job_left_running_by_a_stopped_edge_is_requeued_when_it_is_safe_to_repeat() {
        UUID id = orphan("TEST_OK", 1, 5);
        engine.recoverExpired();
        JobView done = await(id, JobStatus.SUCCEEDED);
        assertThat(done.attempts()).isEqualTo(2);
    }

    @Test
    void a_job_that_is_not_safe_to_repeat_is_failed_as_interrupted() {
        UUID id = orphan("TEST_UNSAFE", 1, 5);
        engine.recoverExpired();
        JobView failed = jobs.find(id).orElseThrow();
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("INTERRUPTED");
    }

    @Test
    void an_orphan_without_attempts_left_is_failed() {
        UUID id = orphan("TEST_OK", 5, 5);
        engine.recoverExpired();
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void a_live_lease_is_left_alone() {
        UUID id = orphan("TEST_OK", 1, 5);
        jdbc.update("update job set lease_until = ? where id = ?",
                Timestamp.from(Instant.now().plusSeconds(600)), id);
        engine.recoverExpired();
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(JobStatus.RUNNING);
        jdbc.update("delete from job where id = ?", id);
    }

    @Test
    void invalid_requests_are_refused() {
        assertThatThrownBy(() -> jobs.enqueue(JobService.Request.of("bad type", "k", "t")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jobs.enqueue(JobService.Request.of("NO_SUCH_HANDLER", "k" + UUID.randomUUID(), "t")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobService.Request.of("TEST_OK", " ", "t"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finished_jobs_older_than_the_retention_are_deleted() {
        UUID old = enqueue("TEST_OK");
        await(old, JobStatus.SUCCEEDED);
        jdbc.update("update job set finished_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(91))), old);
        assertThat(repo.deleteFinishedBefore(Instant.now().minus(Duration.ofDays(90)))).isGreaterThanOrEqualTo(1);
        assertThat(jobs.find(old)).isEmpty();
    }

    @Test
    void the_self_check_job_reports_real_services() {
        UUID id = jobs.enqueue(JobService.Request.of(SystemSelfCheckHandler.TYPE, key(), "tester")).id();
        JobView done = await(id, JobStatus.SUCCEEDED);
        assertThat(done.resultJson()).contains("\"database\"").contains("diskFreeBytes");
    }

    /** A job that was RUNNING when the Edge stopped: the lease has expired. */
    private UUID orphan(String type, int attempts, int maxAttempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into job (id, type, status, priority, created_at, started_at, attempts, max_attempts,
                                 next_run_at, idempotency_key, requested_by, lease_until)
                values (?, ?, 'RUNNING', 5, now(), now(), ?, ?, now(), ?, 'tester', now() - interval '1 minute')
                """, id, type, attempts, maxAttempts, key());
        return id;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
