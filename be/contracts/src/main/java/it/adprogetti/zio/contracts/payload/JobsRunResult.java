package it.adprogetti.zio.contracts.payload;

/** Result of {@code jobs.run}. {@code created} is false when the same request had already created the job. */
public record JobsRunResult(String jobId, boolean created) {
}
