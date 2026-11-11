package it.adprogetti.zio.edge.tenders;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Second tender source: the studio's own Europaconcorsi account (edge.tenders.europaconcorsi.*). The login comes from
 * the keychain, never from here. Kept gentle on purpose: one request every {@code pause}, a page limit, at most one run
 * a day by schedule. {@code baseUrl} must be https, or http on loopback (tests). {@code categories} maps the portal's
 * category id to the label shown on the tender card. The cards carry no CPV code, so categories in
 * {@code keywordCategories} (project financing by default: mostly concessions and works) keep only the titles that
 * contain one of {@code engineeringKeywords}.
 */
@ConfigurationProperties("edge.tenders.europaconcorsi")
public record EuropaconcorsiProperties(boolean enabled, String baseUrl, String country, Map<String, String> categories,
        int maxPagesPerCategory, Duration pause, Duration timeout, List<String> titleKeywords,
        List<String> keywordCategories, List<String> engineeringKeywords) {

    public EuropaconcorsiProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://europaconcorsi.com" : baseUrl;
        country = country == null || !country.matches("[a-z]{2}") ? "it" : country;
        categories = categories == null || categories.isEmpty()
                ? Map.of("200025", "Affidamento d'incarico", "200027", "Appalto integrato", "200028", "Project financing")
                : Map.copyOf(categories);
        maxPagesPerCategory = maxPagesPerCategory <= 0 ? 60 : Math.min(maxPagesPerCategory, 100);
        pause = pause == null || pause.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(3) : pause;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        titleKeywords = titleKeywords == null ? List.of() : List.copyOf(titleKeywords);
        keywordCategories = keywordCategories == null ? List.of("200028") : List.copyOf(keywordCategories);
        engineeringKeywords = engineeringKeywords == null || engineeringKeywords.isEmpty() ? DEFAULT_KEYWORDS
                : List.copyOf(engineeringKeywords);
        for (String id : categories.keySet()) {
            if (!id.matches("[0-9]{3,8}")) {
                throw new IllegalArgumentException("edge.tenders.europaconcorsi.categories: ids are digits only");
            }
        }
    }

    static final List<String> DEFAULT_KEYWORDS = List.of("progettazion", "progetto di fattibilit", "progetto esecutivo",
            "studio di fattibilit", "pfte", "ingegneria", "architettura", "direzione lavori", "direzione dei lavori",
            "direzione dell'esecuzione", "collaudo", "verifica", "validazione", "coordinamento della sicurezza",
            "coordinatore della sicurezza", "supporto al rup", "supporto tecnico", "rilievo", "rilievi", "geologic",
            "geotecnic", "diagnosi", "audit energetic", "pianificazione", "indagini");
}
