package it.adprogetti.zio.contracts.payload;

/** Result of {@code jobs.cancel}: CANCELLED (was queued) or CANCEL_REQUESTED (running, will stop at a safe point). */
public record JobsCancelResult(String jobId, String outcome) {
}
