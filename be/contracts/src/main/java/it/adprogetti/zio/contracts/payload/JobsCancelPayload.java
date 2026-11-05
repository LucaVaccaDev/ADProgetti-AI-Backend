package it.adprogetti.zio.contracts.payload;

import java.util.UUID;

/** Payload of {@code jobs.cancel}. */
public record JobsCancelPayload(String jobId) {

    public JobsCancelPayload {
        try {
            UUID.fromString(jobId == null ? "" : jobId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid job id");
        }
    }
}
