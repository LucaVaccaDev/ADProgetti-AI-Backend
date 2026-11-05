package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.payload.JobsCancelPayload;
import it.adprogetti.zio.contracts.payload.JobsRunPayload;
import it.adprogetti.zio.hub.channel.CommandService;
import it.adprogetti.zio.hub.channel.ProjectionService;
import it.adprogetti.zio.hub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jobs of the Edge: a read-only projection plus signed commands to start or cancel a job. The Hub runs nothing
 * itself; the Edge applies its own whitelist of startable job types.
 */
@RestController
@RequestMapping("/api/jobs")
class JobsAdminController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CommandService commands;
    private final ProjectionService projections;

    JobsAdminController(CommandService commands, ProjectionService projections) {
        this.commands = commands;
        this.projections = projections;
    }

    record JobsResponse(boolean available, Instant updatedAt, JsonNode data) {
    }

    record CommandAccepted(String commandId) {
    }

    record RunRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}") String type) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    JobsResponse jobs() {
        return projections.find("jobs.summary", "all")
                .map(p -> new JobsResponse(true, p.updatedAt(), MAPPER.readTree(p.data())))
                .orElse(new JobsResponse(false, null, null));
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> run(@Valid @RequestBody RunRequest body, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return accept(auth, jwt, request, CommandType.JOBS_RUN, new JobsRunPayload(body.type()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> cancel(@PathVariable String id, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        JobsCancelPayload payload;
        try {
            payload = new JobsCancelPayload(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        return accept(auth, jwt, request, CommandType.JOBS_CANCEL, payload);
    }

    private ResponseEntity<CommandAccepted> accept(Authentication auth, Jwt jwt, HttpServletRequest request,
            CommandType type, Object payload) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        UUID id = commands.issue(type, payload, user.id(), user.username(), user.permissions(), user.ip());
        return ResponseEntity.accepted().body(new CommandAccepted(id.toString()));
    }
}
