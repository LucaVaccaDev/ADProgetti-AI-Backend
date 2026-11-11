package it.adprogetti.zio.edge.jobs;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.JobsRunPayload;
import it.adprogetti.zio.contracts.payload.JobsRunResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Manual start of a job. The Edge applies its own whitelist (an invariant policy): a signed command cannot start
 * any other job type. A manual run of a scheduled type uses the schedule's resource key, so it never overlaps
 * with the automatic run (B.18).
 */
@Component
class JobsRunHandler implements CommandHandler {

    /** Types an administrator may start by hand. PROTOCOL_RUN joins this list with the protocol module. */
    static final Set<String> MANUAL_TYPES = Set.of(SystemSelfCheckHandler.TYPE, "INDEX_NAS", "TENDERS_SYNC",
            "TENDERS_SYNC_EC", "REFERENCES_IMPORT", "TENDER_DOCS_READ");
    static final int MANUAL_PRIORITY = 7;

    private final JobService jobs;
    private final JobsProperties props;
    private final JobsProjector projector;
    private final EdgeAudit audit;

    JobsRunHandler(JobService jobs, JobsProperties props, JobsProjector projector, EdgeAudit audit) {
        this.jobs = jobs;
        this.props = props;
        this.projector = projector;
        this.audit = audit;
    }

    @Override
    public CommandType type() {
        return CommandType.JOBS_RUN;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        String jobType = ((JobsRunPayload) payload).type();
        if (!MANUAL_TYPES.contains(jobType)) {
            audit.record("JOB_RUN", jobType, EdgeAudit.FAILURE, "not allowed, by " + command.requestedBy());
            throw new CommandFailure("NOT_ALLOWED");
        }
        String resource = props.schedules().entrySet().stream()
                .filter(e -> jobType.equals(e.getValue().type())).map(java.util.Map.Entry::getKey).findFirst()
                .orElse("INDEX_NAS".equals(jobType) ? "index-nas" : null); // one indexing run at a time
        JobService.Enqueued queued = jobs.enqueue(new JobService.Request(jobType, null, null,
                "manual:" + command.id(), resource, MANUAL_PRIORITY, null, command.requestedBy()));
        audit.record("JOB_RUN", jobType + " " + queued.id(), EdgeAudit.SUCCESS, "by " + command.requestedBy());
        projector.publishSoon();
        return Payloads.write(new JobsRunResult(queued.id().toString(), queued.created()));
    }
}
