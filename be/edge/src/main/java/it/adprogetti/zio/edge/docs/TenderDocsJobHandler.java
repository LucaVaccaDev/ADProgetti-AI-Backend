package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobHandler;
import it.adprogetti.zio.edge.jobs.RetryPolicy;
import org.springframework.stereotype.Component;

/** Job {@code TENDER_DOCS_READ}: reads the tender documents in the inbox and deletes them. Counts only in the result. */
@Component
class TenderDocsJobHandler implements JobHandler {

    static final String TYPE = "TENDER_DOCS_READ";

    private final TenderDocsInboxService inbox;

    TenderDocsJobHandler(TenderDocsInboxService inbox) {
        this.inbox = inbox;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) {
        return Payloads.write(inbox.run(context::checkpoint, context.requestedBy()));
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    /** A file is deleted only after its result is stored, so starting over never reads a document twice. */
    @Override
    public boolean restartSafe() {
        return true;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.noRetry();
    }
}
