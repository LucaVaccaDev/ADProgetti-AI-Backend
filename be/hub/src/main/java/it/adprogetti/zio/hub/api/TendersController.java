package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.channel.ProjectionService;
import it.adprogetti.zio.hub.common.ApiException;
import it.adprogetti.zio.hub.tenders.TenderDecisionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Published tenders found by the Edge (public data from TED and Europaconcorsi), as a read-only projection, plus the
 * studio's decisions on them (accept, discard, recover).
 */
@RestController
@RequestMapping("/api/tenders")
class TendersController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_SHARDS = 20;
    private static final int MAX_TENDERS = 2000;

    private static final Pattern TENDER_ID = Pattern.compile("[A-Za-z0-9-]{1,40}");

    private final ProjectionService projections;
    private final TenderDecisionService decisions;

    TendersController(ProjectionService projections, TenderDecisionService decisions) {
        this.projections = projections;
        this.decisions = decisions;
    }

    record DecisionRequest(@NotNull TenderDecisionService.Kind decision) {
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
        var decided = decisions.current();
        for (int i = 0; i < shards && tenders.size() < MAX_TENDERS; i++) {
            projections.find("tenders.list", "s" + i).ifPresent(p -> MAPPER.readTree(p.data()).path("tenders").forEach(t -> {
                var decision = decided.get(t.path("id").asString(""));
                if (decision != null && t instanceof ObjectNode tender) {
                    tender.set("decision", MAPPER.valueToTree(decision));
                }
                tenders.add(t);
            }));
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.set("total", head.path("total"));
        data.set("sources", head.path("sources"));
        data.set("tenders", tenders);
        return new TendersResponse(true, index.get().updatedAt(), data);
    }

    /** Accept ("partecipiamo") or discard a tender. Repeating or changing the decision is allowed. */
    @PutMapping("/{id}/decision")
    @PreAuthorize("hasAnyAuthority('TENDERS_WORK','TENDERS_ALL')")
    TenderDecisionService.Decision decide(@PathVariable String id, @Valid @RequestBody DecisionRequest body,
            Authentication auth, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        return decisions.decide(validId(id), body.decision(), user.id(), user.username(), user.ip());
    }

    /** Takes the decision back (recovers a discarded tender). Answers 204 even if there was nothing to take back. */
    @DeleteMapping("/{id}/decision")
    @PreAuthorize("hasAnyAuthority('TENDERS_WORK','TENDERS_ALL')")
    ResponseEntity<Void> clear(@PathVariable String id, Authentication auth, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        decisions.clear(validId(id), user.id(), user.username(), user.ip());
        return ResponseEntity.noContent().build();
    }

    private static String validId(String id) {
        if (!TENDER_ID.matcher(id).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        return id;
    }
}
