package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobFailure;
import it.adprogetti.zio.edge.jobs.JobHandler;
import org.springframework.stereotype.Component;

/** Job {@code TENDERS_SYNC_EC}: reads the tenders of Europaconcorsi. The result holds counts only. */
@Component
class EuropaconcorsiSyncJobHandler implements JobHandler {

    static final String TYPE = "TENDERS_SYNC_EC";

    private final EuropaconcorsiSyncService sync;

    EuropaconcorsiSyncJobHandler(EuropaconcorsiSyncService sync) {
        this.sync = sync;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) throws JobFailure {
        try {
            return Payloads.write(sync.run(context::checkpoint));
        } catch (TedUnavailableException e) {
            throw e.retryable() ? JobFailure.transientFailure(e.code()) : JobFailure.permanent(e.code());
        }
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public boolean restartSafe() {
        return true;
    }
}
