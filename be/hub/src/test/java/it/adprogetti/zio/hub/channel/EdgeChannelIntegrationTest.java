package it.adprogetti.zio.hub.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.CommandVerifier;
import it.adprogetti.zio.contracts.command.InMemoryNonceStore;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.command.VerificationResult;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.hub.IntegrationTestBase;
import it.adprogetti.zio.hub.identity.UserAccount;
import it.adprogetti.zio.hub.identity.UserRepository;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hub side of the channel against a real PostgreSQL and a real WebSocket client acting as the Edge. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EdgeChannelIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "correct-horse-battery";
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired CommandService commands;

    private FakeEdge edge;
    private UUID adminId;

    @BeforeEach
    void clean() {
        for (String table : List.of("command_result", "command_outbox", "projection", "channel_inbox", "edge_state",
                "edge_credential", "audit_log", "refresh_tokens", "user_roles", "users")) {
            jdbc.execute("delete from " + table);
        }
        adminId = createUser("admin", "ADMINISTRATOR");
        createUser("mario", "COLLABORATOR");
    }

    @AfterEach
    void closeEdge() throws Exception {
        if (edge != null) {
            edge.close();
        }
    }

    private UUID createUser(String username, String role) {
        UUID id = UUID.randomUUID();
        users.save(new UserAccount(id, username, username, null, encoder.encode(PASSWORD), Set.of(role), Instant.now()));
        return id;
    }

    private String token(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String bearer(String username) throws Exception {
        return "Bearer " + token(username);
    }

    private String rotateCredential() throws Exception {
        MvcResult result = mvc.perform(post("/api/integrations/edge/credential").header("Authorization", bearer("admin")))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("credential").asString();
    }

    private PublicKey hubPublicKey() throws Exception {
        MvcResult result = mvc.perform(get("/api/integrations/edge/public-key").header("Authorization", bearer("admin")))
                .andExpect(status().isOk()).andReturn();
        byte[] der = Base64.getDecoder().decode(json.readTree(result.getResponse().getContentAsString())
                .get("publicKey").asString());
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    private String issueRefresh(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/ai/models/refresh").header("Authorization", bearer(username)))
                .andExpect(status().isAccepted()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("commandId").asString();
    }

    private JsonNode commandStatus(String id) throws Exception {
        MvcResult result = mvc.perform(get("/api/commands/" + id).header("Authorization", bearer("admin")))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String awaitStatus(String id, String expected) throws Exception {
        String last = null;
        for (int i = 0; i < 50; i++) {
            last = commandStatus(id).get("status").asString();
            if (expected.equals(last)) {
                return last;
            }
            Thread.sleep(100);
        }
        return last;
    }

    private SignedCommand expectCommand(FakeEdge e) throws Exception {
        ChannelEnvelope envelope = e.next(5000);
        assertThat(envelope).as("a COMMAND frame").isNotNull();
        assertThat(envelope.kind()).isEqualTo(ChannelEnvelope.Kind.COMMAND);
        return ChannelCodec.decodeBody(envelope, SignedCommand.class);
    }

    // --- authentication of the Edge ---------------------------------------------------------------

    @Test
    void edgeWithoutOrWithWrongCredentialIsRejectedAndAudited() throws Exception {
        rotateCredential();
        assertThatThrownBy(() -> FakeEdge.connect(port, null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> FakeEdge.connect(port, "not-the-key")).isInstanceOf(ExecutionException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'EDGE_AUTH_FAILED'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void noCredentialConfiguredMeansNobodyConnects() {
        assertThatThrownBy(() -> FakeEdge.connect(port, "anything")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void rotatingTheCredentialInvalidatesTheOldOne() throws Exception {
        String first = rotateCredential();
        FakeEdge.connect(port, first).close();
        String second = rotateCredential();
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> FakeEdge.connect(port, first)).isInstanceOf(ExecutionException.class);
        FakeEdge.connect(port, second).close();
        assertThat(jdbc.queryForObject("select key_hash from edge_credential", String.class)).doesNotContain(second);
    }

    @Test
    void credentialEndpointsNeedThePermission() throws Exception {
        mvc.perform(post("/api/integrations/edge/credential").header("Authorization", bearer("mario")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/integrations/edge/credential")).andExpect(status().isUnauthorized());
    }

    // --- command lifecycle ------------------------------------------------------------------------

    @Test
    void signedCommandIsDeliveredAcknowledgedAndCompleted() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.hello();
        String id = issueRefresh("admin");

        SignedCommand signed = expectCommand(edge);
        assertThat(signed.command().id()).isEqualTo(id);
        assertThat(signed.command().type()).isEqualTo("ai.models.list");
        assertThat(signed.command().requestedBy()).isEqualTo(adminId.toString());
        assertThat(signed.command().permissions()).containsExactly("SYSTEM_READ");
        CommandVerifier verifier = new CommandVerifier(hubPublicKey(), new InMemoryNonceStore(), Clock.systemUTC());
        assertThat(verifier.verify(signed)).isEqualTo(VerificationResult.OK);

        edge.send(ChannelEnvelope.Kind.ACK, new CommandAck(ContractVersion.CURRENT, id, System.currentTimeMillis()));
        edge.send(ChannelEnvelope.Kind.RESULT, new CommandResult(ContractVersion.CURRENT, id,
                CommandResult.Status.SUCCEEDED, "{\"models\":[],\"assignments\":{}}", null));

        assertThat(awaitStatus(id, "SUCCEEDED")).isEqualTo("SUCCEEDED");
        assertThat(commandStatus(id).get("result").get("models").isArray()).isTrue();
    }

    @Test
    void commandIssuedWhileOfflineIsDeliveredOnReconnect() throws Exception {
        String key = rotateCredential();
        String id = issueRefresh("admin");
        assertThat(commandStatus(id).get("status").asString()).isEqualTo("PENDING");

        edge = FakeEdge.connect(port, key);
        edge.hello();
        assertThat(expectCommand(edge).command().id()).isEqualTo(id);
        assertThat(awaitStatus(id, "SENT")).isEqualTo("SENT");
    }

    @Test
    void unacknowledgedCommandIsSentAgainAfterReconnect() throws Exception {
        String key = rotateCredential();
        edge = FakeEdge.connect(port, key);
        edge.hello();
        String id = issueRefresh("admin");
        assertThat(expectCommand(edge).command().id()).isEqualTo(id);
        edge.close(); // no ACK

        edge = FakeEdge.connect(port, key);
        edge.hello();
        assertThat(expectCommand(edge).command().id()).isEqualTo(id);
    }

    @Test
    void aRepeatedResultDoesNotChangeTheOutcome() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.hello();
        String id = issueRefresh("admin");
        expectCommand(edge);
        edge.send(ChannelEnvelope.Kind.RESULT, new CommandResult(ContractVersion.CURRENT, id,
                CommandResult.Status.SUCCEEDED, "{\"ok\":true}", null));
        assertThat(awaitStatus(id, "SUCCEEDED")).isEqualTo("SUCCEEDED");
        edge.send(ChannelEnvelope.Kind.RESULT, new CommandResult(ContractVersion.CURRENT, id,
                CommandResult.Status.FAILED, null, "boom"));
        Thread.sleep(500);
        assertThat(commandStatus(id).get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from command_result", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectedCommandIsReportedAsRejected() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.hello();
        String id = issueRefresh("admin");
        expectCommand(edge);
        edge.send(ChannelEnvelope.Kind.RESULT, new CommandResult(ContractVersion.CURRENT, id,
                CommandResult.Status.REJECTED, null, "POLICY"));
        assertThat(awaitStatus(id, "REJECTED")).isEqualTo("REJECTED");
        assertThat(commandStatus(id).get("errorCode").asString()).isEqualTo("POLICY");
    }

    @Test
    void expiredCommandIsNeverDelivered() throws Exception {
        String key = rotateCredential();
        String id = issueRefresh("admin");
        jdbc.update("update command_outbox set expires_at = now() - interval '1 minute' where id = ?::uuid", id);
        commands.expireOld();
        assertThat(commandStatus(id).get("status").asString()).isEqualTo("EXPIRED");

        edge = FakeEdge.connect(port, key);
        edge.hello();
        assertThat(edge.next(1000)).isNull();
    }

    @Test
    void resultForAnUnknownCommandIsIgnored() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.send(ChannelEnvelope.Kind.RESULT, new CommandResult(ContractVersion.CURRENT,
                UUID.randomUUID().toString(), CommandResult.Status.SUCCEEDED, "{}", null));
        Thread.sleep(500);
        assertThat(jdbc.queryForObject("select count(*) from command_result", Integer.class)).isZero();
    }

    // --- permissions ------------------------------------------------------------------------------

    @Test
    void userWithoutThePermissionCannotIssueCommands() throws Exception {
        mvc.perform(post("/api/ai/models/refresh").header("Authorization", bearer("mario")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/models/test").header("Authorization", bearer("mario"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"model\":\"qwen3:8b\",\"function\":\"ANALYSIS\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from command_outbox", Integer.class)).isZero();
    }

    @Test
    void aCommandIsVisibleOnlyToItsRequester() throws Exception {
        String id = issueRefresh("admin");
        mvc.perform(get("/api/commands/" + id).header("Authorization", bearer("mario")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/commands/" + UUID.randomUUID()).header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidModelNamesAreRefusedBeforeAnyCommandIsCreated() throws Exception {
        for (String body : List.of("{\"model\":\"../../etc/passwd\",\"function\":\"ANALYSIS\"}",
                "{\"model\":\"qwen3:8b\",\"function\":\"HACK\"}", "{\"model\":\"\",\"function\":\"ANALYSIS\"}",
                "{\"model\":\"a b\",\"function\":\"ANALYSIS\"}")) {
            mvc.perform(post("/api/ai/models/test").header("Authorization", bearer("admin"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from command_outbox", Integer.class)).isZero();
    }

    // --- projections and status -------------------------------------------------------------------

    @Test
    void modelsAreNotAvailableUntilTheEdgeReportsThem() throws Exception {
        mvc.perform(get("/api/ai/models").header("Authorization", bearer("mario")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void projectionsAreAppliedOnceAndNeverGoBackInVersion() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.send(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(1, "k-1", "ai.models", "all", 2,
                "{\"models\":[{\"name\":\"bge-m3\"}]}"));
        edge.send(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(1, "k-1", "ai.models", "all", 3,
                "{\"models\":[]}")); // same idempotency key: ignored
        edge.send(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(1, "k-2", "ai.models", "all", 1,
                "{\"models\":[]}")); // older version: ignored
        Thread.sleep(800);
        mvc.perform(get("/api/ai/models").header("Authorization", bearer("mario")))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.data.models[0].name").value("bge-m3"));
        assertThat(jdbc.queryForObject("select version from projection where name = 'ai.models'", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void projectionsOutsideTheWhitelistAreDropped() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.send(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(1, "k-x", "email.body", "1", 1,
                "{\"text\":\"full email\"}"));
        edge.send(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(1, "k-y", "ai.models", "all", 1,
                "not json at all"));
        Thread.sleep(800);
        assertThat(jdbc.queryForObject("select count(*) from projection", Integer.class)).isZero();
    }

    @Test
    void systemStatusFollowsTheRealConnection() throws Exception {
        mvc.perform(get("/api/system/status").header("Authorization", bearer("mario")))
                .andExpect(jsonPath("$.edgeConnected").value(false));

        edge = FakeEdge.connect(port, rotateCredential());
        edge.hello();
        edge.send(ChannelEnvelope.Kind.HEARTBEAT, new EdgeHeartbeat(ContractVersion.CURRENT, "0.1.0",
                System.currentTimeMillis(), 5, List.of(new ServiceStatus("ollama", ServiceStatus.State.UP, null))));
        boolean connected = false;
        for (int i = 0; i < 50 && !connected; i++) {
            Thread.sleep(100);
            MvcResult r = mvc.perform(get("/api/system/status").header("Authorization", bearer("mario"))).andReturn();
            connected = json.readTree(r.getResponse().getContentAsString()).get("edgeConnected").asBoolean();
        }
        assertThat(connected).isTrue();
        mvc.perform(get("/api/system/status").header("Authorization", bearer("mario")))
                .andExpect(jsonPath("$.edgeVersion").value("0.1.0"))
                .andExpect(jsonPath("$.edgeServices[0].name").value("ollama"));

        edge.close();
        boolean stillConnected = true;
        for (int i = 0; i < 50 && stillConnected; i++) {
            Thread.sleep(100);
            MvcResult r = mvc.perform(get("/api/system/status").header("Authorization", bearer("mario"))).andReturn();
            stillConnected = json.readTree(r.getResponse().getContentAsString()).get("edgeConnected").asBoolean();
        }
        assertThat(stillConnected).isFalse();
    }

    // --- hostile input ----------------------------------------------------------------------------

    @Test
    void malformedFrameClosesTheConnection() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.send("{\"kind\":\"DELETE_EVERYTHING\"}");
        assertThat(edge.awaitClosed(5000)).isTrue();
        assertThat(edge.closeStatus().getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    }

    @Test
    void oversizedFrameClosesTheConnection() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        try {
            edge.send("x".repeat(300_000));
        } catch (IllegalStateException | java.io.IOException expected) {
            // the server may close the socket while the frame is still being written
        }
        assertThat(edge.awaitClosed(5000)).isTrue();
    }

    @Test
    void aCommandFrameFromTheEdgeIsAProtocolError() throws Exception {
        edge = FakeEdge.connect(port, rotateCredential());
        edge.send("{\"schemaVersion\":1,\"kind\":\"COMMAND\",\"body\":\"{}\"}");
        assertThat(edge.awaitClosed(5000)).isTrue();
    }

    @Test
    void aNewConnectionReplacesTheOldOne() throws Exception {
        String key = rotateCredential();
        FakeEdge first = FakeEdge.connect(port, key);
        edge = FakeEdge.connect(port, key);
        assertThat(first.awaitClosed(5000)).isTrue();
        edge.hello();
        String id = issueRefresh("admin");
        assertThat(expectCommand(edge).command().id()).isEqualTo(id);
    }
}
