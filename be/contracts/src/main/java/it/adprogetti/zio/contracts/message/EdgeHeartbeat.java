package it.adprogetti.zio.contracts.message;

import java.util.List;

/** Periodic message from the Edge with its health; the Hub shows it as system status. */
public record EdgeHeartbeat(int schemaVersion, String edgeVersion, long sentAtEpochMs, long uptimeSeconds,
        List<ServiceStatus> services) {
    public EdgeHeartbeat {
        services = services == null ? List.of() : List.copyOf(services);
    }
}
