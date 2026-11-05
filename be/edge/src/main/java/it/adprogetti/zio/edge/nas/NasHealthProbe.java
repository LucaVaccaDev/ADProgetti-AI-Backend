package it.adprogetti.zio.edge.nas;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.edge.health.ServiceProbe;
import org.springframework.stereotype.Component;

/** NAS state for the heartbeat, from the last background check (a probe must be fast and never block). */
@Component
class NasHealthProbe implements ServiceProbe {

    private final NasAvailability availability;

    NasHealthProbe(NasAvailability availability) {
        this.availability = availability;
    }

    @Override
    public ServiceStatus probe() {
        var names = availability.shareNames();
        if (names.isEmpty()) {
            return new ServiceStatus("nas", ServiceStatus.State.NOT_CONFIGURED, null);
        }
        long up = names.stream().filter(n -> availability.status(n).available()).count();
        String detail = up + "_OF_" + names.size();
        if (up == names.size()) {
            return new ServiceStatus("nas", ServiceStatus.State.UP, null);
        }
        return new ServiceStatus("nas", up == 0 ? ServiceStatus.State.DOWN : ServiceStatus.State.DEGRADED, detail);
    }
}
