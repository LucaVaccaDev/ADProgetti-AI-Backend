package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.auth.AuthResult;
import it.adprogetti.zio.hub.auth.AuthService;
import it.adprogetti.zio.hub.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    static final String COOKIE = "refresh_token";

    private final AuthService auth;
    private final SecurityProperties properties;

    AuthController(AuthService auth, SecurityProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthResult result = auth.login(request.username(), request.password(), http.getRemoteAddr());
        return withCookie(result);
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = COOKIE, required = false) String token,
            HttpServletRequest http) {
        return withCookie(auth.refresh(token, http.getRemoteAddr()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        auth.logout(token, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"),
                http.getRemoteAddr());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookie(result.refreshToken(), properties.refreshTokenTtl().toSeconds()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(TokenResponse.from(result));
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
