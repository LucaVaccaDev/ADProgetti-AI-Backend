package it.adprogetti.zio.edge.config;

import java.nio.file.Path;
import it.adprogetti.zio.contracts.payload.AiFunction;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Edge settings. Secrets are never here: they come from the macOS keychain. */
@ConfigurationProperties("edge")
public record EdgeProperties(String version, Hub hub, Duration heartbeatInterval, Ai ai) {

    public EdgeProperties {
        version = version == null || version.isBlank() ? "dev" : version;
        hub = hub == null ? new Hub(null, false, null, null, null, null) : hub;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(30) : heartbeatInterval;
        ai = ai == null ? new Ai(null, null, 0, 0, null, null) : ai;
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

    /**
     * Local AI. {@code defaultModels} is the hardware profile (test-24GB now, production later); the
     * administrator's assignments in the database take precedence over it.
     */
    public record Ai(String ollamaUrl, String profile, int lightConcurrency, int heavyConcurrency,
            Duration requestTimeout, Map<AiFunction, String> defaultModels) {
        public Ai {
            ollamaUrl = ollamaUrl == null ? "" : ollamaUrl.trim();
            profile = profile == null || profile.isBlank() ? "test-24GB" : profile;
            lightConcurrency = lightConcurrency <= 0 ? 2 : lightConcurrency;
            heavyConcurrency = heavyConcurrency <= 0 ? 1 : heavyConcurrency;
            requestTimeout = requestTimeout == null ? Duration.ofMinutes(5) : requestTimeout;
            defaultModels = defaultModels == null ? Map.of() : Map.copyOf(defaultModels);
        }
    }
}
