package it.adprogetti.zio.edge.nas;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NAS settings (capitolato 5.1): logical share name, UNC path (documentation only) and macOS mount point. There
 * are no credentials here: the SMB share is mounted by macOS, the Edge only sees the mount point.
 *
 * <p>{@code modes} lists, per access mode, the allowed roots as {@code share:/sub/folder}. A share that appears
 * in no mode (e.g. the temporary folder) cannot be reached at all.
 */
@ConfigurationProperties("edge.nas")
public record NasProperties(Map<String, Share> shares, Map<NasMode, List<String>> modes, long minFreeBytes,
        Duration availabilityTimeout, boolean allowUnmountedDirectory, int maxListEntries) {

    public NasProperties {
        shares = shares == null ? Map.of() : Map.copyOf(shares);
        modes = modes == null ? Map.of() : Map.copyOf(modes);
        minFreeBytes = minFreeBytes <= 0 ? 5L * 1024 * 1024 * 1024 : minFreeBytes;
        availabilityTimeout = availabilityTimeout == null ? Duration.ofSeconds(3) : availabilityTimeout;
        maxListEntries = maxListEntries <= 0 ? 5000 : maxListEntries;
    }

    public record Share(String unc, Path mount) {
    }
}
