package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobFailure;
import it.adprogetti.zio.edge.jobs.JobHandler;
import it.adprogetti.zio.edge.jobs.RetryPolicy;
import org.springframework.stereotype.Component;

/** Job {@code REFERENCES_IMPORT}: reads the studio's references Excel. The result holds counts only. */
@Component
class ReferenceImportJobHandler implements JobHandler {

    static final String TYPE = "REFERENCES_IMPORT";

    private final ReferenceImportService service;

    ReferenceImportJobHandler(ReferenceImportService service) {
        this.service = service;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) throws JobFailure {
        var result = service.run(context.requestedBy());
        if ("FAILED".equals(result.status())) {
            throw JobFailure.permanent(result.errorCode());
        }
        return Payloads.write(result);
    }

    /** Idempotent: a second run over the same file changes nothing. */
    @Override
    public boolean restartSafe() {
        return true;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.noRetry();
    }
}
