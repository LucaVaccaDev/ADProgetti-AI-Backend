package it.adprogetti.zio.edge.tenders;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.docs.TenderDocumentLibrary;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import it.adprogetti.zio.edge.support.StubFreeLlmApi;
import it.adprogetti.zio.edge.support.TestPdfs;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Automatic download of tender documents from a stub of Europaconcorsi: kept until expiry, size cap, fallbacks. */
class TenderDocsFetchTest extends EdgeIntegrationTestBase {

    @Autowired TenderDocsFetchService fetch;
    @Autowired TenderDocumentLibrary library;
    @Autowired TenderStore store;
    @Autowired JdbcTemplate jdbc;

    static final Path LIBRARY = Path.of("target/test-inbox/archivio");

    static final String PAGE1 = "DISCIPLINARE DI GARA. L'importo a base di gara e' pari a euro 184.300,00 al netto di IVA.";

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("delete from tender_analysis");
        jdbc.update("delete from tender");
        EC.reset();
        FREELLM.reset();
        FREELLM.chat = body -> StubFreeLlmApi.Reply.ok("""
                {"importoBaseGara":{"valore":184300.0,"pagina":1,"citazione":"importo a base di gara e' pari a euro 184.300,00"},
                 "criterio":null,"scadenzaOfferte":null,"servizi":[],"fatturato":[],"personale":[],"documenti":[],"incertezze":[]}""");
        if (Files.exists(LIBRARY)) {
            try (var s = Files.walk(LIBRARY)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            }
        }
    }

    private void tender(String number, String deadlineSql) {
        jdbc.update("insert into tender (notice_number, source, title, published_on, notice_type, deadline_at, first_seen_at, last_seen_at) "
                + "values (?, 'EUROPACONCORSI', 'Gara ' || ?, current_date, 'x', " + deadlineSql + ", now(), now())", number, number);
    }

    private static String page(String... files) {
        StringBuilder sb = new StringBuilder("<html><body>");
        for (String f : files) {
            sb.append("<a target=\"_blank\" class=\"competition-link\" data-competition-id=\"1\" data-type=\"attachment\" href=\"")
                    .append(EC.url()).append("/files/").append(f).append("\">Disciplinare</a>");
        }
        return sb.append("</body></html>").toString();
    }

    @Test
    void aTenderAttachmentIsDownloadedKeptAndReadWithoutAnyoneUploadingIt() throws Exception {
        tender("EC-900101", "now() + interval '10 days'");
        EC.files.put("d1.pdf", TestPdfs.text(PAGE1));
        EC.tenderPage = path -> path.equals("/bandi/900101") ? page("d1.pdf") : "";

        var r = fetch.run(() -> { }, "sistema");

        assertThat(r.documents()).isEqualTo(1);
        assertThat(r.analysed()).isEqualTo(1);
        var docs = library.list("EC-900101");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).origin()).isEqualTo("SCARICATO");
        assertThat(Files.exists(LIBRARY.resolve("EC-900101").resolve(docs.get(0).fileName()))).isTrue(); // kept, not deleted
        assertThat(jdbc.queryForObject("select status from tender_analysis where notice_number = 'EC-900101'", String.class))
                .isEqualTo("ANALIZZATA");
        assertThat(jdbc.queryForObject("select source from tender_analysis where notice_number = 'EC-900101'", String.class))
                .isEqualTo("SCARICATO");
        assertThat(jdbc.queryForObject("select doc_status from tender where notice_number = 'EC-900101'", String.class)).isNull();
        // a second run does not download it again
        EC.requests().clear();
        assertThat(fetch.run(() -> { }, "sistema").tenders()).isZero();
        assertThat(EC.requests().stream().filter(x -> x.contains("/files/"))).isEmpty();
    }

    @Test
    void aTenderWithoutAttachmentsIsMarkedNotDownloadableSoTheCardOffersTheUpload() throws Exception {
        tender("EC-900102", "now() + interval '10 days'");
        EC.tenderPage = path -> "<html><body>nessun allegato</body></html>";
        var r = fetch.run(() -> { }, "sistema");
        assertThat(r.notAvailable()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select doc_status from tender where notice_number = 'EC-900102'", String.class))
                .isEqualTo("NON_SCARICABILE");
        assertThat(library.list("EC-900102")).isEmpty();
        // tried again only after a day
        assertThat(fetch.run(() -> { }, "sistema").tenders()).isZero();
        jdbc.update("update tender set doc_checked_at = now() - interval '25 hours'");
        assertThat(fetch.run(() -> { }, "sistema").tenders()).isEqualTo(1);
    }

    @Test
    void aFileAboveTheCapIsNotDownloadedAndTheTenderIsMarkedTooLarge() throws Exception {
        tender("EC-900103", "now() + interval '10 days'");
        EC.files.put("big.pdf", TestPdfs.text(PAGE1));
        EC.announcedSize.put("big.pdf", 150L * 1024 * 1024);
        EC.tenderPage = path -> page("big.pdf");
        var r = fetch.run(() -> { }, "sistema");
        assertThat(r.tooLarge()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select doc_status from tender where notice_number = 'EC-900103'", String.class))
                .isEqualTo("TROPPO_GRANDE");
        assertThat(library.list("EC-900103")).isEmpty();
        // a too large file waits for the upload: it is not tried again
        assertThat(fetch.run(() -> { }, "sistema").tenders()).isZero();
    }

    @Test
    void onlyRealPdfsAreKeptAndLinksToOtherHostsAreNeverFollowed() throws Exception {
        tender("EC-900104", "now() + interval '10 days'");
        EC.files.put("fake.pdf", "<html>not a pdf, just named like one</html>".getBytes());
        EC.tenderPage = path -> page("fake.pdf")
                + "<a data-type=\"attachment\" href=\"https://example.invalid/evil.pdf\">Altro</a>";
        var r = fetch.run(() -> { }, "sistema");
        assertThat(r.documents()).isZero();
        assertThat(library.list("EC-900104")).isEmpty();
        assertThat(jdbc.queryForObject("select doc_note from tender where notice_number = 'EC-900104'", String.class))
                .isEqualTo("NOT_A_PDF");
    }

    @Test
    void whenTheProviderIsDownTheDocumentStaysAndIsReadAtTheNextRun() throws Exception {
        tender("EC-900105", "now() + interval '10 days'");
        EC.files.put("d5.pdf", TestPdfs.text(PAGE1));
        EC.tenderPage = path -> page("d5.pdf");
        FREELLM.chat = body -> StubFreeLlmApi.Reply.status(503);
        var r = fetch.run(() -> { }, "sistema");
        assertThat(r.documents()).isEqualTo(1);
        assertThat(r.waiting()).isEqualTo(1);
        assertThat(library.list("EC-900105")).hasSize(1); // kept
        assertThat(jdbc.queryForObject("select count(*) from tender_analysis", Integer.class)).isZero();
        FREELLM.chat = body -> StubFreeLlmApi.Reply.ok("""
                {"importoBaseGara":{"valore":184300.0,"pagina":1,"citazione":"importo a base di gara e' pari a euro 184.300,00"},
                 "criterio":null,"scadenzaOfferte":null,"servizi":[],"fatturato":[],"personale":[],"documenti":[],"incertezze":[]}""");
        assertThat(fetch.run(() -> { }, "sistema").analysed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from tender_analysis", String.class)).isEqualTo("ANALIZZATA");
    }

    @Test
    void documentsGoWhenTheTenderExpires() throws Exception {
        tender("EC-900106", "now() + interval '10 days'");
        EC.files.put("d6.pdf", TestPdfs.text(PAGE1));
        EC.tenderPage = path -> page("d6.pdf");
        fetch.run(() -> { }, "sistema");
        assertThat(Files.isDirectory(LIBRARY.resolve("EC-900106"))).isTrue();

        jdbc.update("update tender set deadline_at = now() - interval '1 hour' where notice_number = 'EC-900106'");
        store.purgeExpired();

        assertThat(Files.exists(LIBRARY.resolve("EC-900106"))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from tender_document", Integer.class)).isZero();
    }

    @Test
    void theLibraryNeverWritesOutsideItsFolder() throws Exception {
        tender("EC-900107", "now() + interval '10 days'");
        byte[] pdf = TestPdfs.text(PAGE1);
        var doc = library.store("EC-900107", "../../../etc/passwd.pdf", pdf, "CARICATO", null);
        assertThat(doc).isPresent();
        assertThat(doc.get().fileName()).doesNotContain("/").doesNotContain("..");
        assertThat(library.store("../x", "a.pdf", pdf, "CARICATO", null)).isEmpty();
        assertThat(library.store("EC-900107", "b.pdf", "not a pdf at all, long enough to pass the size check ........................................................................".getBytes(), "CARICATO", null)).isEmpty();
        assertThat(library.store("EC-NOPE", "c.pdf", pdf, "CARICATO", null)).isEmpty(); // unknown tender
    }

    @Autowired TendersProjector projector;

    @Test
    void theProjectionTellsTheCardWhereTheDocumentsStand() throws Exception {
        tender("EC-900111", "now() + interval '10 days'"); // fetched
        tender("EC-900112", "now() + interval '11 days'"); // too large
        tender("EC-900113", "now() + interval '12 days'"); // never fetched
        EC.files.put("ok.pdf", TestPdfs.text(PAGE1));
        EC.files.put("big.pdf", TestPdfs.text(PAGE1));
        EC.announcedSize.put("big.pdf", 150L * 1024 * 1024);
        EC.tenderPage = path -> path.equals("/bandi/900111") ? page("ok.pdf") : path.equals("/bandi/900112") ? page("big.pdf") : "";
        jdbc.update("update tender set doc_status = 'NON_SCARICABILE', doc_checked_at = now() where notice_number = 'EC-900113'");
        fetch.run(() -> { }, "sistema");

        var byId = new java.util.HashMap<String, it.adprogetti.zio.contracts.payload.TendersListProjection.Tender>();
        projector.build().shards().forEach(sh -> sh.tenders().forEach(t -> byId.put(t.id(), t)));

        assertThat(byId.get("EC-900111").docState()).isEqualTo("DISPONIBILE");
        assertThat(byId.get("EC-900111").docs()).hasSize(1);
        assertThat(byId.get("EC-900111").docs().get(0).size()).isPositive();
        assertThat(byId.get("EC-900111").analysis()).isEqualTo("ANALIZZATA");
        assertThat(byId.get("EC-900112").docState()).isEqualTo("TROPPO_GRANDE");
        assertThat(byId.get("EC-900112").docs()).isEmpty();
        assertThat(byId.get("EC-900113").docState()).isEqualTo("DA_CARICARE");
        // no path, no content: only names and sizes
        assertThat(it.adprogetti.zio.contracts.payload.Payloads.write(byId.get("EC-900111"))).doesNotContain("archivio");
    }

    @Test
    void aSessionEndedByThePortalIsReopenedOnceAndASecondLossStopsTheRun() throws Exception {
        tender("EC-900121", "now() + interval '10 days'");
        tender("EC-900122", "now() + interval '11 days'");
        EC.files.put("d.pdf", TestPdfs.text(PAGE1));
        EC.tenderPage = path -> page("d.pdf");
        EC.expireSessionOnce = true;
        var r = fetch.run(() -> { }, "sistema");
        assertThat(r.documents()).isEqualTo(2); // both tenders were done after one new login
        assertThat(EC.requests().stream().filter(x -> x.startsWith("POST /people/login")).count()).isEqualTo(2);

        jdbc.update("delete from tender_document");
        jdbc.update("update tender set doc_status = null");
        EC.sessionExpires = true;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fetch.run(() -> { }, "sistema"))
                .isInstanceOf(TedUnavailableException.class).extracting(e -> ((TedUnavailableException) e).code())
                .isEqualTo("EC_SESSION_LOST");
        assertThat(EC.requests().stream().filter(x -> x.startsWith("POST /people/login")).count()).isEqualTo(4); // 2 + 2, no loop
    }
}
