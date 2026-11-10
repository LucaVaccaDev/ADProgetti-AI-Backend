package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.payload.NasListPayload;
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
 * Read-only browsing of the NAS through a signed {@code nas.list} command. The Hub only relays: the Edge always
 * reads with its INDEX_READ mode and applies its own path rules. Only names and metadata travel, never file
 * contents, and the Hub erases the result a few minutes after it arrives (D-03).
 */
@RestController
@RequestMapping("/api/nas")
class NasController {

    private final CommandService commands;

    NasController(CommandService commands) {
        this.commands = commands;
    }

    record ListRequest(@Size(max = NasListPayload.MAX_PATH_LENGTH) String path, @Min(0) Integer offset,
            @Min(1) @Max(NasListPayload.MAX_LIMIT) Integer limit) {
    }

    record CommandAccepted(String commandId) {
    }

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    ResponseEntity<CommandAccepted> list(@Valid @RequestBody ListRequest body, Authentication auth,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        NasListPayload payload;
        try {
            payload = new NasListPayload(body.path(), body.offset(), body.limit());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dati non validi.");
        }
        UUID id = commands.issue(CommandType.NAS_LIST, payload, user.id(), user.username(), user.permissions(),
                user.ip());
        return ResponseEntity.accepted().body(new CommandAccepted(id.toString()));
    }
}
