package it.adprogetti.zio.edge.jobs;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.JobsCancelPayload;
import it.adprogetti.zio.contracts.payload.JobsCancelResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JobsCancelHandler implements CommandHandler {

    private final JobService jobs;
    private final JobsProjector projector;

    JobsCancelHandler(JobService jobs, JobsProjector projector) {
        this.jobs = jobs;
        this.projector = projector;
    }

    @Override
    public CommandType type() {
        return CommandType.JOBS_CANCEL;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        String jobId = ((JobsCancelPayload) payload).jobId();
        JobService.CancelOutcome outcome = jobs.cancel(UUID.fromString(jobId), command.requestedBy());
        switch (outcome) {
            case CANCELLED, CANCEL_REQUESTED -> {
                projector.publishSoon();
                return Payloads.write(new JobsCancelResult(jobId, outcome.name()));
            }
            default -> throw new CommandFailure(outcome.name());
        }
    }
}
