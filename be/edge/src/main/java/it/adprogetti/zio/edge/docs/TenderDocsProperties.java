package it.adprogetti.zio.edge.docs;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tender documents (edge.tenders.docs.*). {@code inbox} is a folder on the Edge's own disk, outside the NAS, where the
 * user can drop PDFs (loose, or in a sub-folder named after the tender). {@code library} is where every document read is
 * kept until its tender expires (D-30): one sub-folder per tender. {@code maxPdfBytes} is the cap for one file (100 MB):
 * a bigger one found online is reported on the tender card so it can be uploaded by hand. {@code windowChars} is how much
 * text goes to the AI in one call; {@code keepUnread} how long a dropped file the AI could not read yet waits.
 * {@code fetchMaxPerRun} and {@code fetchPause} keep the automatic download gentle on the portals.
 */
@ConfigurationProperties("edge.tenders.docs")
public record TenderDocsProperties(Path inbox, Path library, int windowChars, long maxPdfBytes, Duration keepUnread,
        int fetchMaxPerRun, Duration fetchPause) {

    public TenderDocsProperties {
        inbox = inbox == null ? Path.of(System.getProperty("user.home"), "zio-inbox", "gare") : inbox;
        library = library == null ? inbox.resolve("archivio") : library;
        windowChars = windowChars < 4000 ? 30_000 : Math.min(windowChars, 120_000);
        maxPdfBytes = maxPdfBytes <= 0 ? 100L * 1024 * 1024 : maxPdfBytes;
        keepUnread = keepUnread == null ? Duration.ofHours(48) : keepUnread;
        fetchMaxPerRun = fetchMaxPerRun <= 0 ? 60 : Math.min(fetchMaxPerRun, 400);
        fetchPause = fetchPause == null || fetchPause.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(3) : fetchPause;
    }

    @Configuration
    @EnableConfigurationProperties(TenderDocsProperties.class)
    static class Config {
    }
}
