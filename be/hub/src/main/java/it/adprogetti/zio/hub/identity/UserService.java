package it.adprogetti.zio.hub.identity;

import it.adprogetti.zio.hub.audit.AuditService;
import it.adprogetti.zio.hub.common.ApiException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder, AuditService audit,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto create(CreateUserRequest request, UUID actorId, String actorName) {
        String username = request.username().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Nome utente già in uso.");
        }
        checkRolesExist(request.roles());
        UserAccount user = new UserAccount(UUID.randomUUID(), username, request.displayName().trim(),
                request.email(), passwordEncoder.encode(request.password()), request.roles(), clock.instant());
        users.save(user);
        audit.record("USER_CREATED", actorId, actorName, username, AuditService.SUCCESS,
                "roles=" + new java.util.TreeSet<>(request.roles()), null);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request, UUID actorId, String actorName) {
        UserAccount user = find(id);
        boolean wasAdmin = user.getRoles().contains(RoleCodes.ADMINISTRATOR) && user.isEnabled();
        Set<String> newRoles = request.roles() != null ? request.roles() : user.getRoles();
        boolean newEnabled = request.enabled() != null ? request.enabled() : user.isEnabled();
        boolean willBeAdmin = newRoles.contains(RoleCodes.ADMINISTRATOR) && newEnabled;
        if (wasAdmin && !willBeAdmin && users.countEnabledWithRole(RoleCodes.ADMINISTRATOR) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Deve esistere almeno un amministratore attivo.");
        }
        if (request.roles() != null) {
            checkRolesExist(request.roles());
            user.setRoles(request.roles());
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        user.setEnabled(newEnabled);
        user.touch(clock.instant());
        users.save(user);
        audit.record("USER_UPDATED", actorId, actorName, user.getUsername(), AuditService.SUCCESS,
                "enabled=" + newEnabled + ", roles=" + new java.util.TreeSet<>(user.getRoles()), null);
        return UserDto.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword, UUID actorId, String actorName) {
        UserAccount user = find(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.touch(clock.instant());
        users.save(user);
        audit.record("USER_PASSWORD_RESET", actorId, actorName, user.getUsername(), AuditService.SUCCESS, null,
                null);
    }

    /** Permissions are the union of the permissions of all the user's roles (roles combine, D-06). */
    @Transactional(readOnly = true)
    public Set<String> permissionsOf(UserAccount user) {
        Set<String> permissions = new HashSet<>();
        roles.findAllById(user.getRoles()).forEach(role -> permissions.addAll(role.getPermissions()));
        return permissions;
    }

    @Transactional(readOnly = true)
    public UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utente non trovato."));
    }

    private void checkRolesExist(Set<String> requested) {
        if (roles.findAllById(requested).size() != requested.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ruolo non valido.");
        }
    }
}
