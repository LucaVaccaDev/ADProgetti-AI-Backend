package it.adprogetti.zio.edge.docs;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Reading of tender documents (edge.tenders.docs.*). {@code inbox} is a folder on the Edge's own disk, outside the
 * NAS: one sub-folder per tender (named by its number) holding the PDFs the user dropped there. Every file is read
 * once and deleted. {@code windowChars} is how much text goes to the AI in one call; {@code keepUnread} how long a file
 * the AI could not read yet (provider down) waits for another try before it is deleted anyway.
 */
@ConfigurationProperties("edge.tenders.docs")
public record TenderDocsProperties(Path inbox, int windowChars, long maxPdfBytes, Duration keepUnread) {

    public TenderDocsProperties {
        inbox = inbox == null ? Path.of(System.getProperty("user.home"), "zio-inbox", "gare") : inbox;
        windowChars = windowChars < 4000 ? 30_000 : Math.min(windowChars, 120_000);
        maxPdfBytes = maxPdfBytes <= 0 ? 40L * 1024 * 1024 : maxPdfBytes;
        keepUnread = keepUnread == null ? Duration.ofHours(48) : keepUnread;
    }

    @Configuration
    @EnableConfigurationProperties(TenderDocsProperties.class)
    static class Config {
    }
}
