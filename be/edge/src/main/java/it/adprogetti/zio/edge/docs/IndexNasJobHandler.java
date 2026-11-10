package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobHandler;
import it.adprogetti.zio.edge.jobs.RetryPolicy;
import org.springframework.stereotype.Component;

/** Job {@code INDEX_NAS}: indexes new or changed documents. The result holds counts only, never a file name. */
@Component
class IndexNasJobHandler implements JobHandler {

    static final String TYPE = "INDEX_NAS";

    private final IndexingService indexing;

    IndexNasJobHandler(IndexingService indexing) {
        this.indexing = indexing;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) {
        return Payloads.write(indexing.run(context::checkpoint, context.requestedBy()));
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    /** Progress is committed file by file, so starting over only skips what is already done. */
    @Override
    public boolean restartSafe() {
        return true;
    }

    /** The administrator starts it again; a retry loop would hide a persistent problem. */
    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.noRetry();
    }
}
