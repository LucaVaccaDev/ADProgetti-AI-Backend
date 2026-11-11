package it.adprogetti.zio.edge.tenders;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tender monitoring on TED (edge.tenders.*). TED's search API is public and needs no key. The query holds only the
 * filters below: nothing about the studio, its people or its documents ever leaves the Edge.
 * {@code titleKeywords} empty = no keyword filter. {@code apiUrl} must be https, or http on loopback (tests).
 */
@ConfigurationProperties("edge.tenders")
public record TedProperties(boolean enabled, String apiUrl, String country, List<String> cpvPrefixes,
        List<String> noticeTypes, int lookbackDays, int pageSize, int maxPages, Duration pause, Duration timeout,
        int projectionLimit, List<String> titleKeywords) {

    public TedProperties {
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.ted.europa.eu" : apiUrl;
        country = country == null || !country.matches("[A-Z]{3}") ? "ITA" : country;
        cpvPrefixes = cpvPrefixes == null || cpvPrefixes.isEmpty() ? List.of("71") : List.copyOf(cpvPrefixes);
        noticeTypes = noticeTypes == null || noticeTypes.isEmpty() ? List.of("cn-standard", "cn-social") : List.copyOf(noticeTypes);
        lookbackDays = lookbackDays <= 0 ? 30 : Math.min(lookbackDays, 365);
        pageSize = pageSize <= 0 ? 100 : Math.min(pageSize, 250);
        maxPages = maxPages <= 0 ? 10 : maxPages;
        pause = pause == null ? Duration.ofSeconds(1) : pause;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        projectionLimit = projectionLimit <= 0 ? 1000 : Math.min(projectionLimit, 2000);
        titleKeywords = titleKeywords == null ? List.of() : List.copyOf(titleKeywords);
        for (String p : cpvPrefixes) {
            if (!p.matches("[0-9]{2,8}")) {
                throw new IllegalArgumentException("edge.tenders.cpv-prefixes: digits only");
            }
        }
        for (String t : noticeTypes) {
            if (!t.matches("[a-z][a-z0-9-]{1,40}")) {
                throw new IllegalArgumentException("edge.tenders.notice-types: invalid code");
            }
        }
    }
}
