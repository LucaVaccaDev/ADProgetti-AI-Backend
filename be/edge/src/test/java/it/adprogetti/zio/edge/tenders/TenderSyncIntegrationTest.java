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
        var built = projector.build();
        assertThat(built.index().sources()).extracting(it.adprogetti.zio.contracts.payload.TendersListProjection.SourceState::name)
                .containsExactly("TED", "EUROPACONCORSI");
        assertThat(built.index().tenders()).isEmpty();
        assertThat(built.shards().stream().flatMap(s -> s.tenders().stream()))
                .allSatisfy(t -> assertThat(t.url()).startsWith("https://ted.europa.eu/"));
        assertThat(built.shards()).allSatisfy(s -> assertThat(it.adprogetti.zio.contracts.payload.Payloads.write(s).length())
                .isLessThan(TendersProjector.MAX_JSON_CHARS));
    }

    @Test
    void aLongListTravelsInShardsThatTheIndexCounts() {
        java.time.Instant deadline = java.time.Instant.now().plusSeconds(86_400L * 30);
        for (int i = 0; i < 300; i++) {
            jdbc.update("""
                    insert into tender (notice_number, source, title, buyer, deadline_at, published_on, notice_type, first_seen_at, last_seen_at)
                    values (?, 'EUROPACONCORSI', ?, 'Ente', ?, current_date, 'x', now(), now())
                    """, "EC-" + (1000 + i), "Servizio tecnico numero " + i + " ".repeat(0) + "x".repeat(120),
                    java.sql.Timestamp.from(deadline.plusSeconds(i * 60L)));
        }
        var built = projector.build();
        assertThat(built.shards().size()).isGreaterThanOrEqualTo(3);
        assertThat(built.index().shards()).isEqualTo(built.shards().size());
        assertThat(built.shards().stream().mapToInt(s -> s.tenders().size()).sum()).isEqualTo(300);
        assertThat(built.shards()).allSatisfy(s -> {
            assertThat(s.tenders().size()).isLessThanOrEqualTo(TendersProjector.SHARD_SIZE);
            assertThat(it.adprogetti.zio.contracts.payload.Payloads.write(s).length()).isLessThan(TendersProjector.MAX_JSON_CHARS);
        });
        // soonest deadline first across shards
        var ids = built.shards().stream().flatMap(s -> s.tenders().stream()).map(t -> t.id()).toList();
        assertThat(ids.get(0)).isEqualTo("EC-1000");
        assertThat(ids.get(299)).isEqualTo("EC-1299");
    }

    @Test
    void theProjectionIsSentToTheHubAfterASync() throws Exception {
        for (int i = 0; i < 100 && !hub.isConnected(); i++) {
            Thread.sleep(50);
        }
        TED.reply = new it.adprogetti.zio.edge.support.StubTed.Reply(200, page());
        hub.clear();
        sync.run(() -> { });
        var index = hub.await(ChannelEnvelope.Kind.PROJECTION, f -> {
            var u = it.adprogetti.zio.contracts.message.ChannelCodec.decodeBody(f,
                    it.adprogetti.zio.contracts.message.ProjectionUpdate.class);
            return u.projection().equals("tenders.list") && u.entityId().equals("all");
        }, 10000);
        assertThat(index).as("tenders.list index").isNotNull();
    }
}
