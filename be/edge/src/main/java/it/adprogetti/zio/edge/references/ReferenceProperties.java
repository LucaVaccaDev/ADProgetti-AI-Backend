package it.adprogetti.zio.edge.references;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Archive of references (edge.references.*): {@code file} is the logical NAS path of the studio's Excel, read through
 * the NAS service in INDEX_READ mode (so it must sit under an allowed root); {@code sheet} is the sheet with all the
 * services of the last ten years, the 3 and 5 year sheets are subsets of it.
 */
@ConfigurationProperties("edge.references")
public record ReferenceProperties(String file, String sheet) {

    public ReferenceProperties {
        sheet = sheet == null || sheet.isBlank() ? "10 ANNI" : sheet;
    }

    @Configuration
    @EnableConfigurationProperties(ReferenceProperties.class)
    static class Config {
    }
}
