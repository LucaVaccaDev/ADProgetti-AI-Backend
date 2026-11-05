package it.adprogetti.zio.edge.jobs;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.health.ServiceProbe;
import java.io.File;
import java.util.List;
import org.springframework.stereotype.Component;

/** Checks the Edge's services and disk space. The first real job: it proves the engine end to end. */
@Component
class SystemSelfCheckHandler implements JobHandler {

    static final String TYPE = "SYSTEM_SELFCHECK";

    record Result(List<ServiceStatus> services, long diskFreeBytes) {
    }

    private final List<ServiceProbe> probes;

    SystemSelfCheckHandler(List<ServiceProbe> probes) {
        this.probes = probes;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String run(JobContext context) {
        List<ServiceStatus> services = probes.stream().map(ServiceProbe::probe).toList();
        return Payloads.write(new Result(services, new File("/").getUsableSpace()));
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.noRetry();
    }
}
