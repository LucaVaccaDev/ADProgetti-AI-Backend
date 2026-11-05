package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.hub.channel.EdgeStateService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** System status shown on the dashboard: the real state of the Edge channel. */
@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAuthority('SYSTEM_READ')")
class SystemController {

    private final EdgeStateService edge;

    SystemController(EdgeStateService edge) {
        this.edge = edge;
    }

    record StatusResponse(boolean edgeConnected, Instant edgeLastSeenAt, String edgeVersion,
            List<ServiceStatus> edgeServices) {
    }

    @GetMapping("/status")
    StatusResponse status() {
        var s = edge.status();
        return new StatusResponse(s.connected(), s.lastSeenAt(), s.edgeVersion(), s.services());
    }
}
