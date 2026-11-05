package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.AiModelAssignPayload;
import it.adprogetti.zio.contracts.payload.AiModelTestPayload;
import it.adprogetti.zio.contracts.payload.AiModelsListPayload;
import it.adprogetti.zio.hub.channel.CommandService;
import it.adprogetti.zio.hub.channel.ProjectionService;
import it.adprogetti.zio.hub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local AI models, managed through signed commands to the Edge. The Hub only relays: it never talks to
 * an AI provider (D-04). Slow operations answer 202 with a command id to poll at {@code /api/commands/{id}}.
 */
@RestController
@RequestMapping("/api/ai")
class AiAdminController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CommandService commands;
    private final ProjectionService projections;

    AiAdminController(CommandService commands, ProjectionService projections) {
        this.commands = commands;
        this.projections = projections;
    }

    record ModelsResponse(boolean available, Instant updatedAt, JsonNode data) {
    }

    record CommandAccepted(String commandId) {
    }

    record TestRequest(@NotBlank @Size(max = 100) String model, @NotNull AiFunction function) {
    }

    record AssignRequest(@NotNull AiFunction function, @NotBlank @Size(max = 100) String model) {
    }

    /** Last list of models reported by the Edge; "not available" until the Edge has sent one. */
    @GetMapping("/models")
    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    ModelsResponse models() {
        return projections.find("ai.models", "all")
                .map(p -> new ModelsResponse(true, p.updatedAt(), MAPPER.readTree(p.data())))
                .orElse(new ModelsResponse(false, null, null));
    }

    @PostMapping("/models/refresh")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> refresh(Authentication auth, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return accept(auth, jwt, request, CommandType.AI_MODELS_LIST, AiModelsListPayload::new);
    }

    @PostMapping("/models/test")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> test(@Valid @RequestBody TestRequest body, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return accept(auth, jwt, request, CommandType.AI_MODEL_TEST,
                () -> new AiModelTestPayload(body.model(), body.function()));
    }

    @PutMapping("/assignments")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> assign(@Valid @RequestBody AssignRequest body, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return accept(auth, jwt, request, CommandType.AI_MODEL_ASSIGN,
                () -> new AiModelAssignPayload(body.function(), body.model()));
    }

    private ResponseEntity<CommandAccepted> accept(Authentication auth, Jwt jwt, HttpServletRequest request,
            CommandType type, Supplier<Object> payload) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        Object built;
        try {
            built = payload.get();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        UUID id = commands.issue(type, built, user.id(), user.username(), user.permissions(), user.ip());
        return ResponseEntity.accepted().body(new CommandAccepted(id.toString()));
    }
}
