package it.adprogetti.zio.hub.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hub.security")
public record SecurityProperties(String jwtSecret, String issuer, Duration accessTokenTtl,
        Duration refreshTokenTtl, boolean cookieSecure, int maxFailedAttempts, Duration lockout) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "HUB_JWT_SECRET must be set and at least 32 bytes long (hub.security.jwt-secret).");
        }
    }
}
