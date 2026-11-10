package it.adprogetti.zio.edge.nas;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.payload.NasListPayload;
import it.adprogetti.zio.contracts.payload.NasListResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.channel.ConnectionState;
import it.adprogetti.zio.edge.channel.HubConnection;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code nas.list} from the (fake) Hub down to the NAS service and back. */
class NasListCommandIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired HubConnection connection;

    @BeforeEach
    void ready() throws Exception {
        for (int i = 0; i < 100 && !(hub.isConnected() && connection.state() == ConnectionState.CONNECTED); i++) {
            Thread.sleep(50);
        }
        hub.clear();
    }

    private CommandResult send(String json) throws Exception {
        HubCommand c = new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), "nas.list", "user-1",
                List.of("SETTINGS_MANAGE"), System.currentTimeMillis(), Instant.now().plusSeconds(60).toEpochMilli(),
                UUID.randomUUID().toString().replace("-", ""), json);
        hub.send(hub.sign(c));
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.RESULT,
                f -> ChannelCodec.decodeBody(f, CommandResult.class).commandId().equals(c.id()), 15000);
        assertThat(frame).as("RESULT").isNotNull();
        return ChannelCodec.decodeBody(frame, CommandResult.class);
    }

    private static String json(String path, int offset, int limit) {
        return Payloads.write(new NasListPayload(path, offset, limit));
    }

    @Test
    void emptyPathListsOnlyTheRootsAllowedForReading() throws Exception {
        CommandResult r = send("{}");
        assertThat(r.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        NasListResult result = Payloads.read(r.result(), NasListResult.class);
        assertThat(result.entries()).extracting(NasListResult.Entry::name)
                .containsExactly("archivio:/", "documenti:/", "fantasma:/", "gare:/")
                .doesNotContain("temporanea:/");
        assertThat(result.entries()).allMatch(NasListResult.Entry::directory);
    }

    @Test
    void foldersAreListedWithMetadataAndPaginated() throws Exception {
        String dir = "lista-" + UUID.randomUUID().toString().substring(0, 8);
        Files.createDirectories(NAS.resolve("documenti").resolve(dir).resolve("sotto"));
        for (int i = 0; i < 5; i++) {
            Files.writeString(NAS.resolve("documenti").resolve(dir).resolve("f" + i + " è#.txt"), "x".repeat(i + 1));
        }
        Files.writeString(NAS.resolve("documenti").resolve(dir).resolve(".zio-part-hidden"), "h");

        NasListResult first = Payloads.read(send(json("documenti:/" + dir, 0, 3)).result(), NasListResult.class);
        assertThat(first.total()).isEqualTo(6);
        assertThat(first.truncated()).isTrue();
        assertThat(first.entries()).hasSize(3);
        NasListResult second = Payloads.read(send(json("documenti:/" + dir, 3, 3)).result(), NasListResult.class);
        assertThat(second.truncated()).isFalse();
        List<NasListResult.Entry> all = new java.util.ArrayList<>(first.entries());
        all.addAll(second.entries());
        assertThat(all).extracting(NasListResult.Entry::name)
                .containsExactly("f0 è#.txt", "f1 è#.txt", "f2 è#.txt", "f3 è#.txt", "f4 è#.txt", "sotto");
        assertThat(all.get(1).size()).isEqualTo(2);
        assertThat(all.get(5).directory()).isTrue();
        assertThat(all.get(0).modifiedAt()).isNotBlank();
        assertThat(Payloads.write(all)).doesNotContain(NAS.toString()); // never a path of the Mac
    }

    @Test
    void refusedPathsFailWithAShortCodeAndAreAudited() throws Exception {
        assertFails(json("documenti:/../outside", 0, 10), "INVALID_PATH");
        assertFails(json("temporanea:/", 0, 10), "MODE_FORBIDDEN");
        assertFails(json("/etc", 0, 10), "INVALID_PATH");
        assertFails(json("documenti:/non-esiste-" + UUID.randomUUID(), 0, 10), "NOT_FOUND");
        assertFails(json("fantasma:/", 0, 10), "UNAVAILABLE");
    }

    @Test
    void aFileIsNotAFolder() throws Exception {
        String name = "solo-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        writeFile(name);
        assertFails(json("documenti:/" + name, 0, 10), "INVALID_PATH");
    }

    @Test
    void aMalformedPayloadNeverReachesTheNas() throws Exception {
        assertThat(send("{\"path\":\"documenti:/\",\"mode\":\"TENDER_WRITE\"}").status())
                .isEqualTo(CommandResult.Status.REJECTED);
        assertThat(send("{\"offset\":-5}").status()).isEqualTo(CommandResult.Status.REJECTED);
    }

    private void writeFile(String name) throws IOException {
        Files.writeString(NAS.resolve("documenti").resolve(name), "x");
    }

    private void assertFails(String payload, String code) throws Exception {
        CommandResult r = send(payload);
        assertThat(r.status()).isEqualTo(CommandResult.Status.FAILED);
        assertThat(r.errorCode()).isEqualTo(code);
    }
}
