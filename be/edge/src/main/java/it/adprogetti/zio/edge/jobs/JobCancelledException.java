package it.adprogetti.zio.edge.jobs;

/** Thrown by {@link JobContext#checkpoint()} when cancellation was requested: the handler stops at a safe point. */
public class JobCancelledException extends RuntimeException {
    public JobCancelledException() {
        super("cancelled", null, false, false);
    }
}
