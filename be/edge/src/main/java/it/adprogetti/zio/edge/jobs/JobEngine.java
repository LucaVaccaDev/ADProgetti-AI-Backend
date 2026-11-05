package it.adprogetti.zio.edge.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Runs queued jobs on a small pool of workers. Jobs live in the database, so they survive restarts: at startup
 * the jobs that were running are re-queued (if the handler says they are safe to repeat) or failed with
 * INTERRUPTED. Logs carry job id and type only, never payloads or results.
 */
@Component
public class JobEngine implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobEngine.class);

    private final JobRepository repository;
    private final JobRegistry registry;
    private final JobsProperties props;
    private final Clock clock;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private volatile boolean active;
    private volatile boolean paused;
    private ExecutorService workers;
    private ScheduledExecutorService housekeeping;

    JobEngine(JobRepository repository, JobRegistry registry, JobsProperties props, Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void start() {
        recover(false);
        active = true;
        workers = Executors.newFixedThreadPool(props.workers(), r -> daemon(r, "edge-job-worker"));
        for (int i = 0; i < props.workers(); i++) {
            workers.execute(this::workLoop);
        }
        housekeeping = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "edge-job-housekeeping"));
        long renewMs = Math.max(100, props.lease().toMillis() / 3);
        housekeeping.scheduleWithFixedDelay(this::safeMaintenance, renewMs, renewMs, TimeUnit.MILLISECONDS);
        housekeeping.scheduleWithFixedDelay(this::safeCleanup, 1, 1440, TimeUnit.MINUTES);
    }

    @Override
    public void stop() {
        active = false;
        if (workers != null) {
            workers.shutdownNow();
        }
        if (housekeeping != null) {
            housekeeping.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return active;
    }

    /** Stops taking new jobs (running ones finish). For maintenance and tests. */
    void pause() {
        paused = true;
    }

    void resume() {
        paused = false;
    }

    private void workLoop() {
        while (active && !Thread.currentThread().isInterrupted()) {
            try {
                Optional<JobView> job = paused ? Optional.empty() : repository.claimNext(props.lease());
                if (job.isPresent()) {
                    execute(job.get());
                } else {
                    Thread.sleep(props.pollInterval().toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("Job worker error: {}", e.getClass().getSimpleName());
                sleepQuietly();
            }
        }
    }

    private void execute(JobView job) throws InterruptedException {
        Optional<JobHandler> handler = registry.find(job.type());
        if (handler.isEmpty()) {
            repository.fail(job.id(), "NO_HANDLER");
            return;
        }
        running.add(job.id());
        try {
            JobContext context = new JobContext(job, () -> repository.cancelRequested(job.id()));
            String result = handler.get().run(context);
            repository.succeed(job.id(), result);
        } catch (JobCancelledException e) {
            repository.markCancelled(job.id());
        } catch (JobFailure f) {
            afterFailure(job, handler.get(), f.code(), f.retryable());
        } catch (InterruptedException e) {
            throw e; // shutting down: the job stays RUNNING and is recovered at the next start
        } catch (Exception e) {
            log.warn("Job {} {} failed: {}", job.type(), job.id(), e.getClass().getSimpleName());
            afterFailure(job, handler.get(), "EXECUTION_ERROR", true);
        } finally {
            running.remove(job.id());
        }
    }

    private void afterFailure(JobView job, JobHandler handler, String code, boolean retryable) {
        if (retryable && job.attempts() < job.maxAttempts()) {
            Duration wait = handler.retryPolicy().backoffAfter(job.attempts());
            repository.retryLater(job.id(), code, clock.instant().plus(wait));
        } else {
            repository.fail(job.id(), code);
        }
    }

    /** Re-queues or fails the jobs left RUNNING by a stopped Edge. At startup all of them; later only expired leases. */
    void recover(boolean onlyExpired) {
        List<JobView> orphans = repository.running(onlyExpired);
        for (JobView job : orphans) {
            if (running.contains(job.id())) {
                continue;
            }
            boolean safe = registry.find(job.type()).map(JobHandler::restartSafe).orElse(false);
            if (safe && job.attempts() < job.maxAttempts()) {
                repository.retryLater(job.id(), "INTERRUPTED", clock.instant());
            } else {
                repository.fail(job.id(), "INTERRUPTED");
            }
        }
        if (!orphans.isEmpty()) {
            log.info("Recovered {} interrupted job(s)", orphans.size());
        }
    }

    void recoverExpired() {
        recover(true);
    }

    private void safeMaintenance() {
        try {
            repository.renewLeases(new ArrayList<>(running), props.lease());
            recoverExpired();
        } catch (RuntimeException e) {
            log.warn("Job maintenance error: {}", e.getClass().getSimpleName());
        }
    }

    private void safeCleanup() {
        try {
            Instant cutoff = clock.instant().minus(props.retention());
            int n = repository.deleteFinishedBefore(cutoff);
            if (n > 0) {
                log.info("Removed {} finished job(s) older than the retention period", n);
            }
        } catch (RuntimeException e) {
            log.warn("Job cleanup error: {}", e.getClass().getSimpleName());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(props.pollInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
