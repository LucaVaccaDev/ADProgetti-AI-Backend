package it.adprogetti.zio.edge.jobs;

import it.adprogetti.zio.edge.audit.EdgeAudit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** The way into the job queue: enqueue (idempotent), cancel, read. */
@Service
public class JobService {

    public static final int DEFAULT_PRIORITY = 5;
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{2,39}");

    public enum CancelOutcome { CANCELLED, CANCEL_REQUESTED, NOT_CANCELLABLE, NOT_FOUND, ALREADY_FINISHED }

    /** @param resourceKey jobs with the same key never run together (e.g. a schedule name or a phase) */
    public record Request(String type, String payloadJson, String subjectRef, String idempotencyKey,
            String resourceKey, int priority, Duration delay, String requestedBy) {
        public Request {
            if (type == null || !TYPE.matcher(type).matches()) {
                throw new IllegalArgumentException("invalid job type");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
                throw new IllegalArgumentException("invalid idempotency key");
            }
            if (requestedBy == null || requestedBy.isBlank()) {
                throw new IllegalArgumentException("requestedBy required");
            }
            payloadJson = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
            priority = Math.max(0, Math.min(9, priority));
            delay = delay == null ? Duration.ZERO : delay;
        }

        public static Request of(String type, String idempotencyKey, String requestedBy) {
            return new Request(type, null, null, idempotencyKey, null, DEFAULT_PRIORITY, null, requestedBy);
        }
    }

    public record Enqueued(UUID id, boolean created) {
    }

    private final JobRepository repository;
    private final JobRegistry registry;
    private final EdgeAudit audit;
    private final Clock clock;

    JobService(JobRepository repository, JobRegistry registry, EdgeAudit audit, Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.audit = audit;
        this.clock = clock;
    }

    /** Adds a job. A second request with the same idempotency key returns the existing job. */
    public Enqueued enqueue(Request request) {
        JobHandler handler = registry.find(request.type())
                .orElseThrow(() -> new IllegalArgumentException("no handler for job type"));
        Instant next = clock.instant().plus(request.delay());
        var created = repository.insert(new JobRepository.NewJob(request.type(), request.priority(),
                handler.retryPolicy().maxAttempts(), next, request.subjectRef(), request.idempotencyKey(),
                request.resourceKey(), request.payloadJson(), request.requestedBy()));
        if (created.isPresent()) {
            return new Enqueued(created.get(), true);
        }
        return new Enqueued(repository.idByKey(request.idempotencyKey()).orElseThrow(), false);
    }

    public CancelOutcome cancel(UUID id, String requestedBy) {
        Optional<JobView> job = repository.find(id);
        if (job.isEmpty()) {
            return CancelOutcome.NOT_FOUND;
        }
        CancelOutcome outcome = cancelOutcome(job.get());
        audit.record("JOB_CANCEL", job.get().type() + " " + id,
                outcome == CancelOutcome.CANCELLED || outcome == CancelOutcome.CANCEL_REQUESTED
                        ? EdgeAudit.SUCCESS : EdgeAudit.FAILURE,
                outcome.name() + " by " + requestedBy);
        return outcome;
    }

    private CancelOutcome cancelOutcome(JobView job) {
        if (job.status().finished()) {
            return CancelOutcome.ALREADY_FINISHED;
        }
        if (job.status() == JobStatus.QUEUED) {
            return repository.cancelQueued(job.id()) ? CancelOutcome.CANCELLED : cancelRunningOrFinished(job.id());
        }
        return cancelRunningOrFinished(job.id());
    }

    private CancelOutcome cancelRunningOrFinished(UUID id) {
        JobView current = repository.find(id).orElseThrow();
        if (current.status().finished()) {
            return CancelOutcome.ALREADY_FINISHED;
        }
        boolean cancellable = registry.find(current.type()).map(JobHandler::cancellable).orElse(false);
        if (!cancellable) {
            return CancelOutcome.NOT_CANCELLABLE;
        }
        return repository.requestCancel(id) ? CancelOutcome.CANCEL_REQUESTED : CancelOutcome.ALREADY_FINISHED;
    }

    public Optional<JobView> find(UUID id) {
        return repository.find(id);
    }

    public List<JobView> recent(int limit) {
        return repository.recent(Math.max(1, Math.min(limit, 200)));
    }
}
