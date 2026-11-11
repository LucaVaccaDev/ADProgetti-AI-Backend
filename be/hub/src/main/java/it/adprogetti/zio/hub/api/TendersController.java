package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.channel.ProjectionService;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Published tenders found by the Edge (public data from TED), as a read-only projection. */
@RestController
@RequestMapping("/api/tenders")
class TendersController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_SHARDS = 20;
    private static final int MAX_TENDERS = 2000;

    private final ProjectionService projections;

    TendersController(ProjectionService projections) {
        this.projections = projections;
    }

    record TendersResponse(boolean available, Instant updatedAt, JsonNode data) {
    }

    /**
     * The Edge sends the list in shards plus an index (see TendersListProjection). The index says how many shards are
     * current, so shards left over from a longer list are never read. A shard that has not arrived yet is skipped:
     * the answer is then partial, never an error.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('TENDERS_WORK','TENDERS_ALL')")
    TendersResponse tenders() {
        var index = projections.find("tenders.list", "all");
        if (index.isEmpty()) {
            return new TendersResponse(false, null, null);
        }
        JsonNode head = MAPPER.readTree(index.get().data());
        int shards = Math.max(0, Math.min(head.path("shards").asInt(0), MAX_SHARDS));
        ArrayNode tenders = MAPPER.createArrayNode();
        for (int i = 0; i < shards && tenders.size() < MAX_TENDERS; i++) {
            projections.find("tenders.list", "s" + i).ifPresent(p -> MAPPER.readTree(p.data()).path("tenders").forEach(tenders::add));
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.set("total", head.path("total"));
        data.set("sources", head.path("sources"));
        data.set("tenders", tenders);
        return new TendersResponse(true, index.get().updatedAt(), data);
    }
}
