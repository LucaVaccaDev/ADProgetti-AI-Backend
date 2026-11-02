package it.adprogetti.zio.hub.auth;

import it.adprogetti.zio.hub.audit.AuditService;
import it.adprogetti.zio.hub.common.ApiException;
import it.adprogetti.zio.hub.identity.UserAccount;
import it.adprogetti.zio.hub.identity.UserDto;
import it.adprogetti.zio.hub.identity.UserRepository;
import it.adprogetti.zio.hub.identity.UserService;
import it.adprogetti.zio.hub.security.JwtService;
import it.adprogetti.zio.hub.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Login, refresh with rotation and reuse detection, logout. Every failure to authenticate yields the
 * same generic message, so callers cannot tell a wrong user from a wrong password or a locked account.
 */
@Service
public class AuthService {

    private static final String GENERIC_FAILURE = "Credenziali non valide.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final SecurityProperties properties;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;

    AuthService(UserRepository users, UserService userService, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, JwtService jwt, SecurityProperties properties, AuditService audit,
            TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.properties = properties;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public AuthResult login(String rawUsername, String password, String ip) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Optional<UserAccount> found = users.findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash); // keep timing similar
            audit.record("LOGIN", null, username, username, AuditService.FAILURE, "unknown user", ip);
            throw invalid();
        }
        UserAccount user = found.get();
        if (!user.isEnabled() || user.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            audit.record("LOGIN", user.getId(), username, username, AuditService.FAILURE,
                    user.isEnabled() ? "locked" : "disabled", ip);
            throw invalid();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(properties.maxFailedAttempts(), now.plus(properties.lockout()));
            users.save(user);
            audit.record("LOGIN", user.getId(), username, username, AuditService.FAILURE, "bad password", ip);
            throw invalid();
        }
        user.registerSuccessfulLogin(now);
        users.save(user);
        audit.record("LOGIN", user.getId(), username, username, AuditService.SUCCESS, null, ip);
        String refresh = issueRefreshToken(user.getId(), UUID.randomUUID(), now);
        return result(user, refresh);
    }

    public AuthResult refresh(String rawToken, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        Rotation rotation = tx.execute(status -> rotate(rawToken, now));
        if (rotation.status() == Status.REUSED) {
            audit.record("REFRESH_REUSE", rotation.userId(), null, null, AuditService.FAILURE,
                    "revoked token presented again; family revoked", ip);
        }
        if (rotation.status() != Status.OK) {
            throw invalid();
        }
        UserAccount user = userService.find(rotation.userId());
        return result(user, rotation.newToken());
    }

    public void logout(String rawToken, UUID actorId, String actorName, String ip) {
        if (rawToken != null && !rawToken.isBlank()) {
            tx.executeWithoutResult(status -> refreshTokens.findForUpdate(hash(rawToken))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant())));
        }
        audit.record("LOGOUT", actorId, actorName, actorName, AuditService.SUCCESS, null, ip);
    }

    public void revokeAllForUser(UUID userId) {
        tx.executeWithoutResult(status -> refreshTokens.revokeAllForUser(userId, clock.instant()));
    }

    private Rotation rotate(String rawToken, Instant now) {
        Optional<RefreshToken> found = refreshTokens.findForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return new Rotation(Status.INVALID, null, null);
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            return new Rotation(Status.REUSED, token.getUserId(), null);
        }
        UserAccount user = users.findById(token.getUserId()).orElse(null);
        if (token.isExpired(now) || user == null || !user.isEnabled()) {
            token.revoke(now);
            return new Rotation(Status.INVALID, token.getUserId(), null);
        }
        token.revoke(now);
        String next = issueRefreshToken(token.getUserId(), token.getFamilyId(), now);
        return new Rotation(Status.OK, token.getUserId(), next);
    }

    private String issueRefreshToken(UUID userId, UUID familyId, Instant now) {
        byte[] bytes = new byte[32]; // 256 bits
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(userId, familyId, hash(raw), now,
                now.plus(properties.refreshTokenTtl())));
        return raw;
    }

    private AuthResult result(UserAccount user, String refreshToken) {
        String access = jwt.issue(user.getId(), user.getUsername(), userService.permissionsOf(user));
        return new AuthResult(access, jwt.accessTokenSeconds(), UserDto.from(user), refreshToken);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, GENERIC_FAILURE);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private enum Status { OK, INVALID, REUSED }

    private record Rotation(Status status, UUID userId, String newToken) {
    }
}
