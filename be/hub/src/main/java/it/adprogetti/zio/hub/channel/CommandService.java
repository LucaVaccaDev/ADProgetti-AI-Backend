package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.CommandCodec;
import it.adprogetti.zio.contracts.command.CommandSigner;
import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.hub.audit.AuditService;
import it.adprogetti.zio.hub.common.ApiException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Hub-to-Edge commands: permission check, signature, outbox, at-least-once delivery, results.
 * The Hub never executes anything itself: the Edge verifies and re-applies its own policies.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    public record CommandView(String id, String type, String status, Instant createdAt, String result,
            String errorCode) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final CommandSigner signer;
    private final ChannelProperties props;
    private final EdgeSessions sessions;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    CommandService(JdbcClient jdbc, Clock clock, SigningKeyStore keys, ChannelProperties props,
            EdgeSessions sessions, AuditService audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.signer = new CommandSigner(keys.privateKey());
        this.props = props;
        this.sessions = sessions;
        this.audit = audit;
    }

    /** Signs and queues a command; delivers it now if the Edge is connected. Returns the command id. */
    public UUID issue(CommandType type, Object payload, UUID userId, String username, Set<String> userPermissions,
            String ip) {
        if (!userPermissions.contains(type.requiredPermission())) {
            audit.record("COMMAND_DENIED", userId, username, type.code(), AuditService.FAILURE, null, ip);
            throw new ApiException(HttpStatus.FORBIDDEN, "Operazione non consentita.");
        }
        if (payload.getClass() != Payloads.payloadClass(type)) {
            throw new IllegalArgumentException("Payload does not match command type");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expires = now.plus(props.commandTtl());
        HubCommand command = new HubCommand(ContractVersion.CURRENT, id.toString(), type.code(), userId.toString(),
                List.of(type.requiredPermission()), now.toEpochMilli(), expires.toEpochMilli(), nonce(),
                Payloads.write(payload));
        String signedJson = CommandCodec.toJson(signer.sign(command));
        jdbc.sql("""
                insert into command_outbox (id, type, requested_by, status, signed_json, created_at, expires_at)
                values (:id, :type, :by, 'PENDING', :json, :now, :exp)
                """).param("id", id).param("type", type.code()).param("by", userId).param("json", signedJson)
                .param("now", Timestamp.from(now)).param("exp", Timestamp.from(expires)).update();
        audit.record("COMMAND_ISSUED", userId, username, type.code() + " " + id, AuditService.SUCCESS, null, ip);
        deliverOpen(true);
        return id;
    }

    /** Sends commands not yet delivered. With {@code force} also those sent but not acknowledged. */
    public synchronized void deliverOpen(boolean force) {
        if (!sessions.isConnected()) {
            return;
        }
        Instant now = clock.instant();
        record Row(UUID id, String json, String status, Timestamp lastSent) {
        }
        List<Row> rows = jdbc.sql("""
                select id, signed_json, status, last_sent_at from command_outbox
                where status in ('PENDING','SENT') and expires_at > :now order by created_at
                """).param("now", Timestamp.from(now))
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("signed_json"),
                        rs.getString("status"), rs.getTimestamp("last_sent_at"))).list();
        for (Row row : rows) {
            boolean recentlySent = row.lastSent() != null
                    && row.lastSent().toInstant().isAfter(now.minus(props.resendAfter()));
            if (!force && "SENT".equals(row.status()) && recentlySent) {
                continue;
            }
            SignedCommand signed = CommandCodec.fromJson(row.json());
            if (!sessions.send(ChannelCodec.encode(ChannelEnvelope.Kind.COMMAND, signed))) {
                return;
            }
            jdbc.sql("update command_outbox set status = 'SENT', attempts = attempts + 1, last_sent_at = :t where id = :id")
                    .param("t", Timestamp.from(now)).param("id", row.id()).update();
        }
    }

    /** Commands never delivered before their deadline are closed as EXPIRED. */
    public void expireOld() {
        jdbc.sql("update command_outbox set status = 'EXPIRED' where status in ('PENDING','SENT') and expires_at <= :now")
                .param("now", Timestamp.from(clock.instant())).update();
    }

    void onAck(CommandAck ack) {
        UUID id = parse(ack.commandId());
        if (id == null) {
            return;
        }
        jdbc.sql("""
                update command_outbox set status = 'ACKED', acked_at = :t
                where id = :id and status in ('PENDING','SENT')
                """).param("t", Timestamp.from(clock.instant())).param("id", id).update();
    }

    void onResult(CommandResult result) {
        UUID id = parse(result.commandId());
        if (id == null || jdbc.sql("select count(*) from command_outbox where id = :id").param("id", id)
                .query(Integer.class).single() == 0) {
            log.warn("Result for an unknown command ignored");
            return;
        }
        String errorCode = result.errorCode() == null ? null
                : result.errorCode().substring(0, Math.min(64, result.errorCode().length()));
        int inserted = jdbc.sql("""
                insert into command_result (command_id, status, result, error_code, received_at)
                values (:id, :s, :r, :e, :t) on conflict (command_id) do nothing
                """).param("id", id).param("s", result.status().name()).param("r", result.result())
                .param("e", errorCode).param("t", Timestamp.from(clock.instant())).update();
        if (inserted == 1) { // a repeated result is ignored (idempotency)
            jdbc.sql("""
                    update command_outbox set status = :s where id = :id and status in ('PENDING','SENT','ACKED')
                    """).param("s", result.status().name()).param("id", id).update();
            audit.record("COMMAND_RESULT", null, "edge", id + " " + result.status(),
                    result.status() == CommandResult.Status.SUCCEEDED ? AuditService.SUCCESS : AuditService.FAILURE,
                    errorCode, null);
        }
    }

    /** A command is visible only to the user who requested it. */
    public Optional<CommandView> find(UUID id, UUID requester) {
        return jdbc.sql("""
                select o.id, o.type, o.status, o.created_at, r.result, r.error_code
                from command_outbox o left join command_result r on r.command_id = o.id
                where o.id = :id and o.requested_by = :by
                """).param("id", id).param("by", requester)
                .query((rs, n) -> new CommandView(rs.getString("id"), rs.getString("type"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("result"), rs.getString("error_code")))
                .optional();
    }

    private String nonce() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
