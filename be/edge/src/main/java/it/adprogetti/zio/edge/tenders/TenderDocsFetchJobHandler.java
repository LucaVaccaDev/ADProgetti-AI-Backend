package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobFailure;
import it.adprogetti.zio.edge.jobs.JobHandler;
import org.springframework.stereotype.Component;

/** Job {@code TENDER_DOCS_FETCH}: fetches the documents of the open tenders and reads them. The result holds counts only. */
@Component
class TenderDocsFetchJobHandler implements JobHandler {

    static final String TYPE = "TENDER_DOCS_FETCH";

    private final TenderDocsFetchService fetch;

    TenderDocsFetchJobHandler(TenderDocsFetchService fetch) {
        this.fetch = fetch;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) throws JobFailure {
        try {
            return Payloads.write(fetch.run(context::checkpoint, context.requestedBy()));
        } catch (TedUnavailableException e) {
            throw e.retryable() ? JobFailure.transientFailure(e.code()) : JobFailure.permanent(e.code());
        }
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    /** Documents are stored once (unique by content) and results are replaced, so starting over is safe. */
    @Override
    public boolean restartSafe() {
        return true;
    }
}
