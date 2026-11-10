package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.payload.DocsSearchPayload;
import it.adprogetti.zio.hub.channel.CommandService;
import it.adprogetti.zio.hub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search in the document index, which lives on the Edge. The Hub only relays a signed {@code docs.search} command;
 * the answer holds short excerpts of documents, so the Hub erases it a few minutes after it arrives (D-03).
 * An empty query returns only the state of the index.
 */
@RestController
@RequestMapping("/api/docs")
class DocsController {

    private final CommandService commands;

    DocsController(CommandService commands) {
        this.commands = commands;
    }

    record SearchRequest(@Size(max = DocsSearchPayload.MAX_QUERY_LENGTH) String query,
            @Min(1) @Max(DocsSearchPayload.MAX_LIMIT) Integer limit) {
    }

    record CommandAccepted(String commandId) {
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> search(@Valid @RequestBody SearchRequest body, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        DocsSearchPayload payload;
        try {
            payload = new DocsSearchPayload(body.query(), body.limit());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        UUID id = commands.issue(CommandType.DOCS_SEARCH, payload, user.id(), user.username(), user.permissions(),
                user.ip());
        return ResponseEntity.accepted().body(new CommandAccepted(id.toString()));
    }
}
