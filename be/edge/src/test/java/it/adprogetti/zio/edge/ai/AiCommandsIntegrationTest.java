package it.adprogetti.zio.edge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.AiModelAssignPayload;
import it.adprogetti.zio.contracts.payload.AiModelAssignResult;
import it.adprogetti.zio.contracts.payload.AiModelTestPayload;
import it.adprogetti.zio.contracts.payload.AiModelTestResult;
import it.adprogetti.zio.contracts.payload.AiModelsListPayload;
import it.adprogetti.zio.contracts.payload.AiModelsResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.channel.ConnectionState;
import it.adprogetti.zio.edge.channel.HubConnection;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import it.adprogetti.zio.edge.support.StubOllama;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** AI commands from the (fake) Hub down to the (stub) Ollama and back. */
class AiCommandsIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired HubConnection connection;

    @BeforeEach
    void ready() throws Exception {
        OLLAMA.reset();
        for (int i = 0; i < 100 && !(hub.isConnected() && connection.state() == ConnectionState.CONNECTED); i++) {
            Thread.sleep(50);
        }
        jdbc.execute("delete from ai_model_assignment");
        jdbc.execute("delete from ai_usage_log");
        hub.clear();
    }

    private CommandResult send(String type, Object payload, String permission) throws Exception {
        HubCommand c = new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), type, "user-1",
                List.of(permission), System.currentTimeMillis(), Instant.now().plusSeconds(60).toEpochMilli(),
                UUID.randomUUID().toString().replace("-", ""), Payloads.write(payload));
        hub.send(hub.sign(c));
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.RESULT,
                f -> ChannelCodec.decodeBody(f, CommandResult.class).commandId().equals(c.id()), 15000);
        assertThat(frame).as("RESULT").isNotNull();
        return ChannelCodec.decodeBody(frame, CommandResult.class);
    }

    @Test
    void listReportsInstalledModelsLoadedMemoryAndAssignments() throws Exception {
        OLLAMA.ps = "{\"models\":[{\"name\":\"qwen3:8b\",\"size\":6000000000,\"size_vram\":5900000000}]}";
        CommandResult r = send("ai.models.list", new AiModelsListPayload(), "SYSTEM_READ");
        assertThat(r.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        AiModelsResult models = Payloads.read(r.result(), AiModelsResult.class);
        assertThat(models.models()).extracting("name").containsExactlyInAnyOrder("qwen3:8b", "bge-m3:latest");
        assertThat(models.models()).filteredOn(m -> m.name().equals("qwen3:8b")).singleElement().satisfies(m -> {
            assertThat(m.loaded()).isTrue();
            assertThat(m.memoryBytes()).isEqualTo(6_000_000_000L);
        });
        assertThat(models.assignments()).containsEntry(AiFunction.CLASSIFICATION, "qwen3:8b")
                .containsEntry(AiFunction.EMBEDDING, "bge-m3")
                .doesNotContainKey(AiFunction.ANALYSIS); // unassigned until the benchmark: no invented default
    }

    @Test
    void listAlsoSendsTheModelsProjection() throws Exception {
        send("ai.models.list", new AiModelsListPayload(), "SYSTEM_READ");
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.PROJECTION,
                f -> ChannelCodec.decodeBody(f, ProjectionUpdate.class).projection().equals("ai.models"), 5000);
        assertThat(frame).isNotNull();
        ProjectionUpdate update = ChannelCodec.decodeBody(frame, ProjectionUpdate.class);
        assertThat(update.entityId()).isEqualTo("all");
        assertThat(Payloads.read(update.data(), AiModelsResult.class).models()).hasSize(2);
    }

    @Test
    void anUnreachableOllamaIsAFailureWithAShortCode() throws Exception {
        OLLAMA.tags = null; // the stub will fail below
        OLLAMA.tags = "not json";
        CommandResult r = send("ai.models.list", new AiModelsListPayload(), "SYSTEM_READ");
        assertThat(r.status()).isEqualTo(CommandResult.Status.FAILED);
        assertThat(r.errorCode()).isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void assigningAnInstalledModelKeepsThePreviousOneAndCanBeUndone() throws Exception {
        CommandResult first = send("ai.model.assign", new AiModelAssignPayload(AiFunction.CLASSIFICATION, "bge-m3:latest"),
                "SETTINGS_MANAGE");
        assertThat(first.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        AiModelAssignResult a = Payloads.read(first.result(), AiModelAssignResult.class);
        assertThat(a.model()).isEqualTo("bge-m3:latest");
        assertThat(a.previousModel()).isEqualTo("qwen3:8b"); // the profile default

        CommandResult undo = send("ai.model.assign", new AiModelAssignPayload(AiFunction.CLASSIFICATION, "qwen3:8b"),
                "SETTINGS_MANAGE");
        assertThat(Payloads.read(undo.result(), AiModelAssignResult.class).previousModel()).isEqualTo("bge-m3:latest");
        assertThat(jdbc.queryForObject("select model from ai_model_assignment where function = 'CLASSIFICATION'",
                String.class)).isEqualTo("qwen3:8b");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'AI_MODEL_ASSIGNED'",
                Integer.class)).isPositive();
    }

    @Test
    void aModelThatIsNotInstalledCannotBeAssigned() throws Exception {
        CommandResult r = send("ai.model.assign", new AiModelAssignPayload(AiFunction.ANALYSIS, "qwen3.6:27b"),
                "SETTINGS_MANAGE");
        assertThat(r.status()).isEqualTo(CommandResult.Status.FAILED);
        assertThat(r.errorCode()).isEqualTo("MODEL_NOT_INSTALLED");
        assertThat(jdbc.queryForObject("select count(*) from ai_model_assignment", Integer.class)).isZero();
    }

    @Test
    void assignmentNeedsThePermissionOnTheEdgeToo() throws Exception {
        CommandResult r = send("ai.model.assign", new AiModelAssignPayload(AiFunction.ANALYSIS, "qwen3:8b"),
                "SYSTEM_READ"); // signed by the Hub, but without SETTINGS_MANAGE
        assertThat(r.status()).isEqualTo(CommandResult.Status.REJECTED);
        assertThat(r.errorCode()).isEqualTo("POLICY");
    }

    @Test
    void aModelTestReportsRealOutcomes() throws Exception {
        OLLAMA.chat = body -> StubOllama.Reply.ok(StubOllama.chatBody("{\"category\":\"BUSINESS_RELEVANT\"}"));
        AiModelTestResult ok = Payloads.read(send("ai.model.test",
                new AiModelTestPayload("qwen3:8b", AiFunction.CLASSIFICATION), "SETTINGS_MANAGE").result(),
                AiModelTestResult.class);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.error()).isNull();

        OLLAMA.chat = body -> StubOllama.Reply.ok(StubOllama.chatBody("sono un modello disobbediente"));
        AiModelTestResult bad = Payloads.read(send("ai.model.test",
                new AiModelTestPayload("qwen3:8b", AiFunction.CLASSIFICATION), "SETTINGS_MANAGE").result(),
                AiModelTestResult.class);
        assertThat(bad.ok()).isFalse();
        assertThat(bad.error()).isEqualTo("INVALID_OUTPUT");

        AiModelTestResult missing = Payloads.read(send("ai.model.test",
                new AiModelTestPayload("nope:1b", AiFunction.EXTRACTION), "SETTINGS_MANAGE").result(),
                AiModelTestResult.class);
        assertThat(missing.error()).isEqualTo("MODEL_NOT_INSTALLED");

        AiModelTestResult embed = Payloads.read(send("ai.model.test",
                new AiModelTestPayload("bge-m3:latest", AiFunction.EMBEDDING), "SETTINGS_MANAGE").result(),
                AiModelTestResult.class);
        assertThat(embed.ok()).isTrue();
    }

    @Test
    void imageTestsAreNotPretended() throws Exception {
        AiModelTestResult r = Payloads.read(send("ai.model.test",
                new AiModelTestPayload("qwen3:8b", AiFunction.IMAGES), "SETTINGS_MANAGE").result(),
                AiModelTestResult.class);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("NOT_TESTABLE");
    }

    @Test
    void usageMetadataIsProjectedToTheHubOnConnectionWithoutContent() throws Exception {
        send("ai.model.test", new AiModelTestPayload("qwen3:8b", AiFunction.CLASSIFICATION), "SETTINGS_MANAGE");
        hub.drop();
        for (int i = 0; i < 100 && hub.count(ChannelEnvelope.Kind.HELLO) == 0; i++) {
            Thread.sleep(50);
        }
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.PROJECTION,
                f -> ChannelCodec.decodeBody(f, ProjectionUpdate.class).projection().equals("ai.usage"), 8000);
        assertThat(frame).isNotNull();
        String data = ChannelCodec.decodeBody(frame, ProjectionUpdate.class).data();
        assertThat(data).contains("CLASSIFICATION").contains("qwen3:8b").doesNotContain("Richiesta di preventivo");
    }
}
