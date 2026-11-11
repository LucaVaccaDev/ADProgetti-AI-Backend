package it.adprogetti.zio.edge.tenders;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The parser on cards recorded from Europaconcorsi (src/test/resources/europaconcorsi/search-page.html). */
class EuropaconcorsiParserTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/test/resources/europaconcorsi/search-page.html"));
    }

    @Test
    void readsTheFactsOfEachCard() throws Exception {
        var cards = EuropaconcorsiParser.parse(page());
        assertThat(cards).hasSize(4);
        var first = cards.get(0);
        assertThat(first.id()).isEqualTo("572280");
        assertThat(first.title()).startsWith("Progettazione e direzione dei lavori");
        assertThat(first.buyer()).startsWith("Provincia autonoma di Bolzano").doesNotEndWith("·");
        assertThat(first.place()).isEqualTo("Dobbiaco (BZ), Italia");
        assertThat(first.deadline()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(first.path()).startsWith("/bandi/572280-");
        assertThat(first.originUrl()).startsWith("https://www.bandi-altoadige.it/");
    }

    @Test
    void ownAttachmentsAreNotTheOriginLink() throws Exception {
        var cards = EuropaconcorsiParser.parse(page());
        assertThat(cards.get(1).originUrl()).isNull();
        assertThat(cards).allSatisfy(c -> assertThat(c.originUrl() == null || !c.originUrl().contains("europaconcorsi.com")).isTrue());
    }

    @Test
    void paginationAndLoginFormAreRecognised() throws Exception {
        assertThat(EuropaconcorsiParser.lastPage(page())).isEqualTo(2);
        assertThat(EuropaconcorsiParser.lastPage("<html></html>")).isEqualTo(1);
        assertThat(EuropaconcorsiParser.isLoginForm("<input type=\"password\" name=\"person[password]\">")).isTrue();
        assertThat(EuropaconcorsiParser.isLoginForm(page())).isFalse();
    }

    @Test
    void hostileOrBrokenMarkupIsHandledAsData() {
        String html = "<div class=\"competition\" data-competition-id=\"1\"><span class=\"organization\">Ente &lt;script&gt;x</span>"
                + "<div class=\"title\"><a href=\"/bandi/1-titolo\">T <b>b</b>\u0000 ok</a></div>"
                + "<span class=\"deadline\">Scadenza 31/02/2026</span><div class=\"description\"></div>"
                + "<a href=\"http://insecure.example/x\">x</a><a href=\"https://user:pw@evil.example/\">y</a></div>"
                + "<div class=\"competition\" data-competition-id=\"2\"><div class=\"title\"><a href=\"javascript:alert(1)\">no</a></div></div>";
        var cards = EuropaconcorsiParser.parse(html);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).title()).isEqualTo("T b ok");
        assertThat(cards.get(0).deadline()).isNull();
        assertThat(cards.get(0).originUrl()).isNull();
    }

    @Test
    void similarTitlesAreRecognisedAcrossRewrites() {
        assertThat(TitleSimilarity.similar("SERVIZIO DI VERIFICA DEI PROGETTI PER LA RIQUALIFICAZIONE DELL'OSPEDALE DI ODERZO",
                "Servizio di verifica dei progetti per la riqualificazione dell’Ospedale nel Comune di Oderzo")).isTrue();
        assertThat(TitleSimilarity.similar("Progettazione della scuola di Monza", "Direzione lavori ponte sul Po a Piacenza")).isFalse();
        assertThat(TitleSimilarity.similar("Gara", "Gara")).isFalse();
    }
}
