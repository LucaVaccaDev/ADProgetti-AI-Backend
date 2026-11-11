package it.adprogetti.zio.edge.tenders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** TED sync against a local stub: upsert, dedupe, failure codes, projection. Never touches the network. */
class TenderSyncIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired TenderSyncService sync;
    @Autowired TendersProjector projector;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from tender");
        TED.reset();
    }

    private static String page() throws Exception {
        return Files.readString(Path.of("src/test/resources/ted/search-page.json"));
    }

    @Test
    void newNoticesAreStoredAndASecondRunAddsNothing() throws Exception {
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, page());
        var first = sync.run(() -> { });
        assertThat(first.status()).isEqualTo("OK");
        assertThat(first.fetched()).isEqualTo(3);
        assertThat(first.added()).isEqualTo(3);
        var second = sync.run(() -> { });
        assertThat(second.added()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tender", Integer.class)).isEqualTo(3);
    }

    @Test
    void theQueryHoldsOnlyPublicFilters() throws Exception {
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, page());
        sync.run(() -> { });
        String request = TED.requests().get(0);
        assertThat(request).startsWith("POST /v3/notices/search")
                .contains("buyer-country=ITA", "classification-cpv=713*", "classification-cpv=715*", "notice-type IN (cn-standard cn-social)");
    }

    @Test
    void tedFailuresBecomeShortCodesAndAreRecorded() {
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(429, "{}");
        assertThatThrownBy(() -> sync.run(() -> { })).isInstanceOfSatisfying(TedUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("TED_RATE_LIMIT"));
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, "not json");
        assertThatThrownBy(() -> sync.run(() -> { })).isInstanceOfSatisfying(TedUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("TED_BAD_RESPONSE"));
        assertThat(jdbc.queryForObject("select last_status from tender_sync where id = 1", String.class)).isEqualTo("FAILED");
    }

    @Test
    void theProjectionStaysWithinTheHubLimitAndPointsToTed() throws Exception {
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, page());
        sync.run(() -> { });
        var projection = projector.build();
        assertThat(projection.sources()).extracting(it.adprogetti.zio.contracts.payload.TendersListProjection.SourceState::name)
                .containsExactly("TED", "EUROPACONCORSI");
        assertThat(projection.tenders()).allSatisfy(t -> assertThat(t.url()).startsWith("https://ted.europa.eu/"));
        assertThat(it.adprogetti.zio.contracts.payload.Payloads.write(projection).length())
                .isLessThan(TendersProjector.MAX_JSON_CHARS);
    }

    @Test
    void theProjectionIsSentToTheHubAfterASync() throws Exception {
        for (int i = 0; i < 100 && !hub.isConnected(); i++) {
            Thread.sleep(50);
        }
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, page());
        hub.clear();
        sync.run(() -> { });
        assertThat(hub.await(ChannelEnvelope.Kind.PROJECTION,
                f -> f.body() != null && f.body().contains("tenders.list"), 10000)).isNotNull();
    }
}
