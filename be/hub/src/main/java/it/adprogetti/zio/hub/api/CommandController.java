package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.channel.CommandService;
import it.adprogetti.zio.hub.common.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outcome of a command; visible only to the user who requested it (otherwise 404). */
@RestController
@RequestMapping("/api/commands")
class CommandController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CommandService commands;

    CommandController(CommandService commands) {
        this.commands = commands;
    }

    record CommandResponse(String id, String type, String status, Instant createdAt, JsonNode result,
            String errorCode) {
    }

    @GetMapping("/{id}")
    CommandResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return commands.find(id, UUID.fromString(jwt.getSubject()))
                .map(c -> new CommandResponse(c.id(), c.type(), c.status(), c.createdAt(), json(c.result()),
                        c.errorCode()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comando non trovato."));
    }

    private static JsonNode json(String text) {
        if (text == null) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JacksonException e) {
            return null;
        }
    }
}
