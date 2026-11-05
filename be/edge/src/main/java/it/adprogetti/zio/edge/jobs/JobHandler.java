package it.adprogetti.zio.edge.jobs;

/** Executes one job type. Handlers are Spring beans; the engine finds them by {@link #type()}. */
public interface JobHandler {

    /** Upper-case code, e.g. {@code PROTOCOL_RUN}. */
    String type();

    /** @return the result as JSON (or null); never put contents of emails or documents in it */
    String run(JobContext context) throws Exception;

    default RetryPolicy retryPolicy() {
        return RetryPolicy.standard();
    }

    /** Whether a running job can be asked to stop (it must then call {@link JobContext#checkpoint()}). */
    default boolean cancellable() {
        return false;
    }

    /** Whether the job can safely run again from the start if the Edge stopped while it was running. */
    default boolean restartSafe() {
        return true;
    }
}
