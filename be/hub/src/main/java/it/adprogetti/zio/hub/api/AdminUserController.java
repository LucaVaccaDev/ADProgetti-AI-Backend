package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.auth.AuthService;
import it.adprogetti.zio.hub.identity.CreateUserRequest;
import it.adprogetti.zio.hub.identity.ResetPasswordRequest;
import it.adprogetti.zio.hub.identity.UpdateUserRequest;
import it.adprogetti.zio.hub.identity.UserDto;
import it.adprogetti.zio.hub.identity.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users are created only here, by an administrator (D-08). Also guarded in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('USERS_MANAGE')")
class AdminUserController {

    private final UserService users;
    private final AuthService auth;

    AdminUserController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping
    List<UserDto> list() {
        return users.list();
    }

    @PostMapping
    ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        UserDto created = users.create(request, actor(jwt), name(jwt));
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UserDto updated = users.update(id, request, actor(jwt), name(jwt));
        if (!updated.enabled() || request.roles() != null) {
            auth.revokeAllForUser(id); // force a fresh login so permissions are re-read
        }
        return updated;
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        users.resetPassword(id, request.newPassword(), actor(jwt), name(jwt));
        auth.revokeAllForUser(id);
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String name(Jwt jwt) {
        return jwt.getClaimAsString("username");
    }
}
