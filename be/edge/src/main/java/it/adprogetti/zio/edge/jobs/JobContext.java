package it.adprogetti.zio.edge.jobs;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** What a handler sees of its job. */
public final class JobContext {

    private final JobView job;
    private final BooleanSupplier cancelRequested;

    JobContext(JobView job, BooleanSupplier cancelRequested) {
        this.job = job;
        this.cancelRequested = cancelRequested;
    }

    public UUID id() {
        return job.id();
    }

    public String type() {
        return job.type();
    }

    public String payloadJson() {
        return job.payloadJson();
    }

    public String subjectRef() {
        return job.subjectRef();
    }

    public int attempt() {
        return job.attempts();
    }

    public String requestedBy() {
        return job.requestedBy();
    }

    public boolean cancelRequested() {
        return cancelRequested.getAsBoolean();
    }

    /** Call at points where stopping is safe (between files, between emails...). */
    public void checkpoint() {
        if (cancelRequested.getAsBoolean()) {
            throw new JobCancelledException();
        }
    }
}
