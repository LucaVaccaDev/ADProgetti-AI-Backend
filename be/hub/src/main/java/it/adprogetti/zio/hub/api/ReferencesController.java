package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.payload.ReferencesDecidePayload;
import it.adprogetti.zio.contracts.payload.ReferencesListPayload;
import it.adprogetti.zio.hub.channel.CommandService;
import it.adprogetti.zio.hub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The studio's references (past services) live on the Edge (D-03): the Hub only relays signed commands. The answer
 * holds client names and amounts, so the Hub erases it a few minutes after it arrives. Reserved to the tender
 * manager (TENDERS_ALL); the Edge checks the permission again.
 */
@RestController
@RequestMapping("/api/references")
class ReferencesController {

    private final CommandService commands;

    ReferencesController(CommandService commands) {
        this.commands = commands;
    }

    record CommandAccepted(String commandId) {
    }

    record DecisionRequest(@NotBlank String decision) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENDERS_ALL')")
    ResponseEntity<CommandAccepted> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit,
            Authentication auth, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        if (offset != null && offset < 0 || limit != null && (limit < 1 || limit > ReferencesListPayload.MAX_LIMIT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        ReferencesListPayload payload;
        try {
            payload = new ReferencesListPayload(status, offset, limit);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        return accepted(commands.issue(CommandType.REFERENCES_LIST, payload, user.id(), user.username(),
                user.permissions(), user.ip()));
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('TENDERS_ALL')")
    ResponseEntity<CommandAccepted> decide(@PathVariable long id, @Valid @RequestBody DecisionRequest body,
            Authentication auth, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        ReferencesDecidePayload payload;
        try {
            payload = new ReferencesDecidePayload(id, body.decision());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        return accepted(commands.issue(CommandType.REFERENCES_DECIDE, payload, user.id(), user.username(),
                user.permissions(), user.ip()));
    }

    private static ResponseEntity<CommandAccepted> accepted(UUID id) {
        return ResponseEntity.accepted().body(new CommandAccepted(id.toString()));
    }
}
