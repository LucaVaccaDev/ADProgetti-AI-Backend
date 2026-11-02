package it.adprogetti.zio.hub.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System status shown on the dashboard. The Hub-Edge channel is not built yet, so the Edge is
 * reported as not connected: this is the truth, not a placeholder to be replaced by a mock.
 */
@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAuthority('SYSTEM_READ')")
class SystemController {

    record StatusResponse(boolean edgeConnected) {
    }

    @GetMapping("/status")
    StatusResponse status() {
        return new StatusResponse(false);
    }
}
