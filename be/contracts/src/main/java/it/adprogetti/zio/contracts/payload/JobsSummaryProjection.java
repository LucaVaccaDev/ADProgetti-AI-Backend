package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Projection {@code jobs.summary}: the state of the job engine, metadata only (CLAUDE.md section 4). Never
 * payloads, results or the object a job is working on. Times are ISO-8601 instants (UTC).
 */
public record JobsSummaryProjection(Counts counts, List<Job> recent, List<Schedule> schedules) {

    public JobsSummaryProjection {
        recent = recent == null ? List.of() : List.copyOf(recent);
        schedules = schedules == null ? List.of() : List.copyOf(schedules);
    }

    /** Queued and running now; finished in the last 24 hours. */
    public record Counts(int queued, int running, int succeededLast24h, int failedLast24h) {
    }

    public record Job(String id, String type, String status, int attempts, int maxAttempts, String createdAt,
            String startedAt, String finishedAt, String errorCode, String requestedBy, boolean cancellable) {
    }

    public record Schedule(String name, String jobType, boolean enabled, String zone, List<String> times,
            String lastSlot) {
        public Schedule {
            times = times == null ? List.of() : List.copyOf(times);
        }
    }
}
