package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.channel.ProjectionService;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Published tenders found by the Edge (public data from TED), as a read-only projection. */
@RestController
@RequestMapping("/api/tenders")
class TendersController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ProjectionService projections;

    TendersController(ProjectionService projections) {
        this.projections = projections;
    }

    record TendersResponse(boolean available, Instant updatedAt, JsonNode data) {
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TENDERS_WORK','TENDERS_ALL')")
    TendersResponse tenders() {
        return projections.find("tenders.list", "all")
                .map(p -> new TendersResponse(true, p.updatedAt(), MAPPER.readTree(p.data())))
                .orElse(new TendersResponse(false, null, null));
    }
}
