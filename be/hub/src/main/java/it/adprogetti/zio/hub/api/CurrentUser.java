package it.adprogetti.zio.hub.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, read from the verified token. */
record CurrentUser(UUID id, String username, Set<String> permissions, String ip) {

    static CurrentUser from(Authentication authentication, Jwt jwt, HttpServletRequest request) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"),
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet()),
                request.getRemoteAddr());
    }
}
