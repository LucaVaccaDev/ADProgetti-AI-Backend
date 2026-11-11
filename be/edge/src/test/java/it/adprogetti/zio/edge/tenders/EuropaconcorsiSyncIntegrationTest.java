package it.adprogetti.zio.edge.tenders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Europaconcorsi sync against a local stub of the portal: login, pages, dedupe, failures. Never reaches the real site. */
class EuropaconcorsiSyncIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired EuropaconcorsiSyncService sync;
    @Autowired TenderStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired EuropaconcorsiProperties props;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("delete from tender");
        EC.reset();
        String page = Files.readString(Path.of("src/test/resources/europaconcorsi/search-page.html"));
        EC.page = path -> path.contains("page=1") ? page : "<html><body></body></html>";
    }

    @Test
    void cardsAreStoredWithTheirSourceAndLinks() throws Exception {
        var result = sync.run(() -> { });
        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.added()).isEqualTo(4);
        var rows = jdbc.queryForList("select notice_number, source, url, procedure_type from tender order by notice_number");
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("source")).isEqualTo("EUROPACONCORSI"));
        assertThat(jdbc.queryForObject("select url from tender where notice_number = 'EC-572280'", String.class))
                .startsWith("https://www.bandi-altoadige.it/");
        // no origin link on the card: the portal's own page is used
        assertThat(jdbc.queryForObject("select url from tender where notice_number = 'EC-572910'", String.class))
                .startsWith(EC.url() + "/bandi/572910-");
        assertThat(jdbc.queryForObject("select procedure_type from tender limit 1", String.class)).isEqualTo("Affidamento d'incarico");
        assertThat(sync.run(() -> { }).added()).isZero();
    }

    @Test
    void itLogsInOnceAndTheRequestsAreGentle() throws Exception {
        sync.run(() -> { });
        var requests = EC.requests();
        assertThat(requests.stream().filter(r -> r.startsWith("POST /people/login")).count()).isEqualTo(1);
        assertThat(requests.stream().filter(r -> r.startsWith("GET /search")).count()).isLessThanOrEqualTo(2);
        assertThat(requests.toString()).doesNotContain(it.adprogetti.zio.edge.support.StubEuropaconcorsi.PASSWORD);
    }

    @Test
    void aRefusedLoginIsNotRetriedAndIsRecorded() {
        EC.rejectLogin = true;
        assertThatThrownBy(() -> sync.run(() -> { })).isInstanceOfSatisfying(TedUnavailableException.class, e -> {
            assertThat(e.code()).isEqualTo("EC_LOGIN_FAILED");
            assertThat(e.retryable()).isFalse();
        });
        assertThat(EC.requests().stream().filter(r -> r.startsWith("POST /people/login")).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_status from tender_sync where id = 2", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select error_code from tender_sync where id = 2", String.class)).isEqualTo("EC_LOGIN_FAILED");
    }

    @Test
    void anExpiredSessionStopsTheRun() {
        EC.sessionExpires = true;
        assertThatThrownBy(() -> sync.run(() -> { })).isInstanceOfSatisfying(TedUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("EC_LOGIN_FAILED"));
    }

    private static TenderRow row(String number, String title, String source, Instant deadline) {
        return new TenderRow(number, title, "Ente", source.equals("TED") ? new BigDecimal("550138.10") : null, null, deadline,
                LocalDate.of(2026, 11, 1), "x", "n", List.of(), null, 1, source, null);
    }

    @Test
    void theSameTenderFromTwoSourcesIsKeptOnceAndTedWins() {
        Instant deadline = Instant.parse("2027-03-10T12:00:00Z");
        String ted = "SERVIZIO DI VERIFICA DEI PROGETTI PER LA RIQUALIFICAZIONE DELL'OSPEDALE DI ODERZO";
        String ec = "Servizio di verifica dei progetti per la riqualificazione dell’Ospedale nel Comune di Oderzo";
        assertThat(store.upsert(row("EC-1", ec, "EUROPACONCORSI", deadline))).isTrue();
        assertThat(store.upsert(row("100-2026", ted, "TED", deadline))).isTrue();
        assertThat(jdbc.queryForList("select notice_number from tender")).extracting(r -> r.get("notice_number"))
                .containsExactly("100-2026");
        assertThat(store.upsert(row("EC-1", ec, "EUROPACONCORSI", deadline))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from tender", Integer.class)).isEqualTo(1);
        // a different tender on the same day is untouched
        assertThat(store.upsert(row("EC-2", "Progettazione della scuola di Monza", "EUROPACONCORSI", deadline))).isTrue();
    }

    @Test
    void projectFinancingKeepsOnlyTechnicalServices() {
        assertThat(sync.isEngineering("200028", "Servizi di progettazione e direzione lavori del nuovo plesso")).isTrue();
        assertThat(sync.isEngineering("200028", "Verifica del progetto esecutivo")).isTrue();
        assertThat(sync.isEngineering("200028", "Concessione demaniale marittima con finalità turistico ricreativa")).isFalse();
        assertThat(sync.isEngineering("200028", "Gestione dell'impianto sportivo comunale")).isFalse();
        // other categories are not filtered by words
        assertThat(sync.isEngineering("200025", "Commissione del Paesaggio")).isTrue();
    }

    @Test
    void rowsThePortalNoLongerListsAreDroppedAfterACompleteRun() throws Exception {
        Instant later = Instant.now().plusSeconds(86_400 * 30);
        store.upsert(row("EC-999", "Gara sparita dal portale", "EUROPACONCORSI", later));
        store.upsert(row("100-2026", "Gara di TED rimane", "TED", later.plusSeconds(86_400)));
        sync.run(() -> { });
        assertThat(jdbc.queryForObject("select count(*) from tender where notice_number = 'EC-999'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tender where notice_number = '100-2026'", Integer.class)).isEqualTo(1);
    }
}
