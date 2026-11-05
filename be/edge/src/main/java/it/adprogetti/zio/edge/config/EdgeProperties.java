package it.adprogetti.zio.edge.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Edge settings. Secrets are never here: they come from the macOS keychain. */
@ConfigurationProperties("edge")
public record EdgeProperties(String version, Hub hub, Duration heartbeatInterval, Ai ai) {

    public EdgeProperties {
        version = version == null || version.isBlank() ? "dev" : version;
        hub = hub == null ? new Hub(null, false, null, null, null, null) : hub;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(30) : heartbeatInterval;
        ai = ai == null ? new Ai(null) : ai;
    }

    public record Hub(String url, boolean allowInsecure, Path caFile, Duration reconnectInitial,
            Duration reconnectMax, Duration connectTimeout) {
        public Hub {
            url = url == null ? "" : url.trim();
            reconnectInitial = reconnectInitial == null ? Duration.ofSeconds(1) : reconnectInitial;
            reconnectMax = reconnectMax == null ? Duration.ofSeconds(60) : reconnectMax;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        }

        public boolean configured() {
            return !url.isEmpty();
        }
    }

    public record Ai(String ollamaUrl) {
        public Ai {
            ollamaUrl = ollamaUrl == null ? "" : ollamaUrl.trim();
        }
    }
}
