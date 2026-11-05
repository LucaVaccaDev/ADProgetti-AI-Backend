package it.adprogetti.zio.edge.channel;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.edge.config.EdgeProperties;
import it.adprogetti.zio.edge.health.ServiceProbe;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends the Edge's health to the Hub at a fixed interval while connected. Never queued: only the latest matters. */
@Component
class HeartbeatService {

    private final List<ServiceProbe> probes;
    private final EdgeProperties props;
    private final Clock clock;
    private final Instant startedAt;
    private volatile HubConnection connection;

    HeartbeatService(List<ServiceProbe> probes, EdgeProperties props, Clock clock) {
        this.probes = probes;
        this.props = props;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    void bind(HubConnection hubConnection) {
        this.connection = hubConnection;
    }

    /** Called by the connection once it is open. */
    void sendNow() {
        HubConnection c = connection;
        if (c != null) {
            pushVia(c.currentLink());
        }
    }

    @Scheduled(fixedDelayString = "${edge.heartbeat-interval:30s}")
    void tick() {
        HubConnection c = connection;
        if (c != null) {
            pushVia(c.currentLink());
        }
    }

    private void pushVia(HubLink target) {
        if (target == null) {
            return;
        }
        List<ServiceStatus> services = new ArrayList<>();
        for (ServiceProbe probe : probes) {
            services.add(probe.probe());
        }
        EdgeHeartbeat beat = new EdgeHeartbeat(ContractVersion.CURRENT, props.version(), clock.millis(),
                Math.max(0, clock.instant().getEpochSecond() - startedAt.getEpochSecond()), services);
        target.send(ChannelCodec.encode(ChannelEnvelope.Kind.HEARTBEAT, beat));
    }
}
