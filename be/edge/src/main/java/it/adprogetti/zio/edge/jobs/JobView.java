package it.adprogetti.zio.edge.jobs;

import java.time.Instant;
import java.util.UUID;

/** A job as stored. {@code errorCode} is a short code; the payload and result are never shown to the Hub. */
public record JobView(UUID id, String type, JobStatus status, int priority, Instant createdAt, Instant startedAt,
        Instant finishedAt, int attempts, int maxAttempts, Instant nextRunAt, String errorCode, String subjectRef,
        String idempotencyKey, String resourceKey, String payloadJson, String resultJson, boolean cancelRequested,
        String requestedBy) {
}
