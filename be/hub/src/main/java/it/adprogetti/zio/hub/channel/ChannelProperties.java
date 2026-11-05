package it.adprogetti.zio.hub.channel;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the Hub-Edge channel. The signing key is a file with 600 permissions, never in the repository. */
@ConfigurationProperties("hub.channel")
public record ChannelProperties(Path signingKeyFile, Duration commandTtl, Duration resendAfter,
        Duration heartbeatTimeout, int maxMessageBytes) {

    public ChannelProperties {
        commandTtl = commandTtl == null ? Duration.ofMinutes(5) : commandTtl;
        resendAfter = resendAfter == null ? Duration.ofSeconds(30) : resendAfter;
        heartbeatTimeout = heartbeatTimeout == null ? Duration.ofSeconds(90) : heartbeatTimeout;
        maxMessageBytes = maxMessageBytes <= 0 ? 262_144 : maxMessageBytes;
    }
}
