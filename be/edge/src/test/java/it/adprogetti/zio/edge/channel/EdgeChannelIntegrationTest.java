package it.adprogetti.zio.edge.channel;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.contracts.payload.SystemPingPayload;
import it.adprogetti.zio.contracts.payload.SystemPingResult;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The Edge against a fake Hub speaking the real protocol, with a real PostgreSQL. */
class EdgeChannelIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired HubConnection connection;
    @Autowired OutboxService outbox;
    @Autowired it.adprogetti.zio.edge.command.CommandDispatcher dispatcher;

    @BeforeEach
    void ready() throws Exception {
        hub.rejectHandshakes(false);
        awaitConnected();
        for (String table : List.of("outbox_message", "inbox_command", "nonce_seen", "audit_log")) {
            jdbc.execute("delete from " + table);
        }
        hub.clear();
    }

    private void awaitConnected() throws Exception {
        for (int i = 0; i < 100; i++) {
            if (hub.isConnected() && connection.state() == ConnectionState.CONNECTED) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The Edge did not connect to the fake Hub");
    }

    private HubCommand command(String type, Object payload, List<String> permissions, String nonce, Instant expires) {
        return new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), type, "user-1", permissions,
                System.currentTimeMillis(), expires.toEpochMilli(), nonce, Payloads.write(payload));
    }

    private HubCommand ping(String label) {
        return command("system.ping", new SystemPingPayload(label), List.of("SYSTEM_READ"), nonce(),
                Instant.now().plusSeconds(60));
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private CommandResult resultOf(String commandId) throws Exception {
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.RESULT,
                f -> ChannelCodec.decodeBody(f, CommandResult.class).commandId().equals(commandId), 5000);
        assertThat(frame).as("RESULT for " + commandId).isNotNull();
        return ChannelCodec.decodeBody(frame, CommandResult.class);
    }

    // --- connection ---------------------------------------------------------------------------------

    @Test
    void sendsHelloAndHeartbeatWithRealServiceStatus() throws Exception {
        hub.drop(); // a fresh connection starts with HELLO
        Thread.sleep(100);
        awaitConnected();
        assertThat(hub.await(ChannelEnvelope.Kind.HELLO, 5000)).isNotNull();
        ChannelEnvelope beat = hub.await(ChannelEnvelope.Kind.HEARTBEAT, 5000);
        assertThat(beat).isNotNull();
        EdgeHeartbeat heartbeat = ChannelCodec.decodeBody(beat, EdgeHeartbeat.class);
        assertThat(heartbeat.services()).anySatisfy(s -> {
            assertThat(s.name()).isEqualTo("database");
            assertThat(s.state()).isEqualTo(ServiceStatus.State.UP);
        });
        assertThat(heartbeat.services()).anySatisfy(s -> {
            assertThat(s.name()).isEqualTo("ollama");
            assertThat(s.state()).isEqualTo(ServiceStatus.State.UP); // the stub Ollama answers
        });
    }

    @Test
    void reconnectsByItselfAfterTheHubDropsTheConnection() throws Exception {
        int before = hub.connections();
        hub.drop();
        for (int i = 0; i < 100 && hub.connections() == before; i++) {
            Thread.sleep(50);
        }
        assertThat(hub.connections()).isGreaterThan(before);
        awaitConnected();
        assertThat(hub.await(ChannelEnvelope.Kind.HELLO, 5000)).isNotNull();
    }

    @Test
    void keepsRetryingWhileTheHubRefusesAndRecoversWhenItAccepts() throws Exception {
        hub.rejectHandshakes(true);
        hub.drop();
        int attempts = hub.attempts();
        Thread.sleep(1200);
        assertThat(hub.attempts()).as("retries with backoff").isGreaterThan(attempts + 1);
        assertThat(hub.isConnected()).isFalse();
        hub.rejectHandshakes(false);
        awaitConnected();
    }

    @Test
    void messagesQueuedWhileOfflineAreSentAfterReconnecting() throws Exception {
        hub.rejectHandshakes(true);
        hub.drop();
        for (int i = 0; i < 100 && hub.isConnected(); i++) {
            Thread.sleep(20);
        }
        outbox.enqueue(ChannelEnvelope.Kind.PROJECTION,
                new ProjectionUpdate(ContractVersion.CURRENT, "k-offline", "ai.models", "all", 1, "{\"models\":[]}"));
        assertThat(jdbc.queryForObject("select count(*) from outbox_message where sent_at is null", Integer.class))
                .isEqualTo(1);
        hub.rejectHandshakes(false);
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.PROJECTION, 8000);
        assertThat(frame).isNotNull();
        assertThat(ChannelCodec.decodeBody(frame, ProjectionUpdate.class).idempotencyKey()).isEqualTo("k-offline");
    }

    // --- commands -----------------------------------------------------------------------------------

    @Test
    void validCommandIsAcknowledgedExecutedAndAnswered() throws Exception {
        HubCommand command = ping("hello");
        hub.send(hub.sign(command));

        ChannelEnvelope ack = hub.await(ChannelEnvelope.Kind.ACK, 5000);
        assertThat(ChannelCodec.decodeBody(ack, CommandAck.class).commandId()).isEqualTo(command.id());
        CommandResult result = resultOf(command.id());
        assertThat(result.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        assertThat(Payloads.read(result.result(), SystemPingResult.class).label()).isEqualTo("hello");
        assertThat(jdbc.queryForObject("select status from inbox_command where id = ?::uuid", String.class,
                command.id())).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'COMMAND_EXECUTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void aRepeatedCommandIsNotExecutedAgainAndTheStoredResultIsResent() throws Exception {
        HubCommand command = ping("once");
        SignedCommand signed = hub.sign(command);
        hub.send(signed);
        CommandResult first = resultOf(command.id());
        hub.send(signed);
        CommandResult second = resultOf(command.id());

        assertThat(second).isEqualTo(first); // same edgeTime: it was not run again
        assertThat(jdbc.queryForObject("select count(*) from inbox_command", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'COMMAND_EXECUTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void aForgedSignatureIsRejected() throws Exception {
        HubCommand command = ping("forged");
        SignedCommand valid = hub.sign(command);
        SignedCommand forged = new SignedCommand(command, valid.signature().replace(valid.signature().charAt(0),
                valid.signature().charAt(0) == 'A' ? 'B' : 'A'));
        hub.send(forged);
        CommandResult result = resultOf(command.id());
        assertThat(result.status()).isEqualTo(CommandResult.Status.REJECTED);
        assertThat(result.errorCode()).isEqualTo("BAD_SIGNATURE");
    }

    @Test
    void aTamperedPermissionListInvalidatesTheSignature() throws Exception {
        HubCommand original = command("ai.model.assign", new it.adprogetti.zio.contracts.payload.AiModelAssignPayload(
                it.adprogetti.zio.contracts.payload.AiFunction.ANALYSIS, "qwen3:8b"), List.of("SYSTEM_READ"), nonce(),
                Instant.now().plusSeconds(60));
        SignedCommand signed = hub.sign(original);
        HubCommand tampered = new HubCommand(original.schemaVersion(), original.id(), original.type(),
                original.requestedBy(), List.of("SETTINGS_MANAGE"), original.issuedAtEpochMs(),
                original.expiresAtEpochMs(), original.nonce(), original.payload());
        hub.send(new SignedCommand(tampered, signed.signature()));
        assertThat(resultOf(original.id()).errorCode()).isEqualTo("BAD_SIGNATURE");
    }

    @Test
    void anExpiredCommandIsRejected() throws Exception {
        HubCommand command = command("system.ping", new SystemPingPayload("late"), List.of("SYSTEM_READ"), nonce(),
                Instant.now().minusSeconds(5));
        hub.send(hub.sign(command));
        assertThat(resultOf(command.id()).errorCode()).isEqualTo("EXPIRED");
    }

    @Test
    void aNonceUsedBeforeIsRefusedEvenForAnotherCommandId() throws Exception {
        String shared = nonce();
        HubCommand first = command("system.ping", new SystemPingPayload("a"), List.of("SYSTEM_READ"), shared,
                Instant.now().plusSeconds(60));
        hub.send(hub.sign(first));
        assertThat(resultOf(first.id()).status()).isEqualTo(CommandResult.Status.SUCCEEDED);

        HubCommand second = command("system.ping", new SystemPingPayload("b"), List.of("SYSTEM_READ"), shared,
                Instant.now().plusSeconds(60));
        hub.send(hub.sign(second));
        assertThat(resultOf(second.id()).errorCode()).isEqualTo("REPLAYED");
    }

    @Test
    void theEdgeAppliesItsOwnPolicyEvenToSignedCommands() throws Exception {
        HubCommand noPermission = command("system.ping", new SystemPingPayload("x"), List.of(), nonce(),
                Instant.now().plusSeconds(60));
        hub.send(hub.sign(noPermission));
        assertThat(resultOf(noPermission.id()).errorCode()).isEqualTo("POLICY");

        HubCommand unknown = command("nas.delete", new SystemPingPayload("x"), List.of("SYSTEM_READ"), nonce(),
                Instant.now().plusSeconds(60));
        hub.send(hub.sign(unknown));
        assertThat(resultOf(unknown.id()).errorCode()).isEqualTo("UNKNOWN_TYPE");
    }

    @Test
    void invalidPayloadsAreRejectedNotExecuted() throws Exception {
        HubCommand badPayload = new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), "system.ping",
                "user-1", List.of("SYSTEM_READ"), System.currentTimeMillis(),
                Instant.now().plusSeconds(60).toEpochMilli(), nonce(), "{\"label\":\"" + "x".repeat(200) + "\"}");
        hub.send(hub.sign(badPayload));
        assertThat(resultOf(badPayload.id()).errorCode()).isEqualTo("INVALID_PAYLOAD");

    }

    @Test
    void aCommandInterruptedByARestartIsClosedAsFailedAndNeverRunAgain() throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into inbox_command (id, type, requested_by, status, received_at)
                values (?::uuid, 'system.ping', 'user-1', 'RECEIVED', now())
                """, id);
        dispatcherRecover();
        CommandResult result = resultOf(id);
        assertThat(result.status()).isEqualTo(CommandResult.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo("INTERRUPTED");
        assertThat(jdbc.queryForObject("select status from inbox_command where id = ?::uuid", String.class, id))
                .isEqualTo("FAILED");
    }

    private void dispatcherRecover() throws Exception {
        var method = dispatcher.getClass().getDeclaredMethod("recoverInterrupted");
        method.setAccessible(true);
        method.invoke(dispatcher);
    }

    @Test
    void errorDetailsNeverReachTheHub() throws Exception {
        // a payload that parses but makes the handler fail would show only EXECUTION_ERROR; here we check the
        // rejection path carries a short code and no free text
        HubCommand command = command("nas.delete", new SystemPingPayload("x"), List.of("SYSTEM_READ"), nonce(),
                Instant.now().plusSeconds(60));
        hub.send(hub.sign(command));
        CommandResult result = resultOf(command.id());
        assertThat(result.result()).isNull();
        assertThat(result.errorCode()).matches("[A-Z_]{3,32}");
    }
}
