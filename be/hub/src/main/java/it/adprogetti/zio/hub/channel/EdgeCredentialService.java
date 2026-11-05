package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.hub.audit.AuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** The Edge's dedicated credential: 256 random bits, stored only as a SHA-256 hash, rotatable. */
@Service
public class EdgeCredentialService {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    EdgeCredentialService(JdbcClient jdbc, Clock clock, AuditService audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    /** Creates or replaces the credential. The clear value is returned once and never stored. */
    public String rotate(UUID actorId, String actorUsername, String ip) {
        byte[] key = new byte[32];
        random.nextBytes(key);
        String clear = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        jdbc.sql("""
                insert into edge_credential (id, key_hash, created_at, rotated_by) values (1, :h, :t, :u)
                on conflict (id) do update set key_hash = :h, created_at = :t, rotated_by = :u
                """).param("h", hash(clear)).param("t", java.sql.Timestamp.from(clock.instant())).param("u", actorId)
                .update();
        audit.record("EDGE_CREDENTIAL_ROTATED", actorId, actorUsername, "edge", AuditService.SUCCESS, null, ip);
        return clear;
    }

    /** Constant-time comparison of the presented key against the stored hash. */
    public boolean matches(String presented) {
        if (presented == null || presented.isBlank() || presented.length() > 200) {
            return false;
        }
        Optional<String> stored = jdbc.sql("select key_hash from edge_credential where id = 1")
                .query(String.class).optional();
        byte[] expected = stored.map(s -> s.getBytes(StandardCharsets.US_ASCII))
                .orElse(new byte[64]); // compare anyway so timing does not reveal "no credential"
        boolean equal = MessageDigest.isEqual(hash(presented).getBytes(StandardCharsets.US_ASCII), expected);
        return stored.isPresent() && equal;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
