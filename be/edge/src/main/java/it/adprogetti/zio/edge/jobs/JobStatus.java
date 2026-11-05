package it.adprogetti.zio.edge.jobs;

public enum JobStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

    public boolean finished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
