package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.identity.UserDto;
import it.adprogetti.zio.hub.identity.UserService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserService users;

    MeController(UserService users) {
        this.users = users;
    }

    record MeResponse(UserDto user, java.util.Set<String> permissions) {
    }

    @GetMapping
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        var account = users.find(UUID.fromString(jwt.getSubject()));
        return new MeResponse(UserDto.from(account), new java.util.TreeSet<>(users.permissionsOf(account)));
    }
}
