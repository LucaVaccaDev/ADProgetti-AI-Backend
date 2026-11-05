package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.CommandVerifier;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.command.VerificationResult;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.channel.HubTrust;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First stop for a command: verifies signature, expiry and nonce, and records it in the inbox in the same
 * transaction. A command id seen before is never executed again; its stored result is returned instead.
 */
@Service
class CommandIntake {

    sealed interface Outcome {
        /** Verified and stored: must now be executed. */
        record Accepted(HubCommand command) implements Outcome {
        }

        /** Already known or refused: answer with this result. */
        record Answered(CommandResult result) implements Outcome {
        }

        /** Already known and still running: no answer yet. */
        record InProgress(String commandId) implements Outcome {
        }

        /** Not even an id we can answer to. */
        record Invalid() implements Outcome {
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final HubTrust trust;
    private final PersistentNonceStore nonces;
    private final EdgeAudit audit;

    CommandIntake(JdbcClient jdbc, Clock clock, HubTrust trust, PersistentNonceStore nonces, EdgeAudit audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.trust = trust;
        this.nonces = nonces;
        this.audit = audit;
    }

    @Transactional
    Outcome accept(SignedCommand signed) {
        UUID id = idOf(signed);
        if (id == null) {
            audit.record("COMMAND_INVALID", null, EdgeAudit.FAILURE, "no usable id");
            return new Outcome.Invalid();
        }
        HubCommand command = signed.command();
        int inserted = jdbc.sql("""
                insert into inbox_command (id, type, requested_by, status, received_at)
                values (:id, :type, :by, 'RECEIVED', :t) on conflict (id) do nothing
                """).param("id", id).param("type", truncate(command.type(), 40))
                .param("by", truncate(command.requestedBy(), 64)).param("t", Timestamp.from(clock.instant())).update();
        if (inserted == 0) {
            return known(id);
        }
        VerificationResult verification = trust.commandPublicKey()
                .map(key -> new CommandVerifier(key, nonces, clock).verify(signed))
                .orElse(VerificationResult.BAD_SIGNATURE);
        if (verification != VerificationResult.OK) {
            CommandResult rejected = rejected(id.toString(), verification.name());
            finish(id, "REJECTED", rejected);
            audit.record("COMMAND_REJECTED", command.type() + " " + id, EdgeAudit.FAILURE, verification.name());
            return new Outcome.Answered(rejected);
        }
        return new Outcome.Accepted(command);
    }

    /** Stores the final outcome of a command. */
    void complete(String commandId, CommandResult result) {
        String status = switch (result.status()) {
            case SUCCEEDED -> "DONE";
            case REJECTED -> "REJECTED";
            case FAILED -> "FAILED";
        };
        finish(UUID.fromString(commandId), status, result);
    }

    private Outcome known(UUID id) {
        Optional<String> stored = jdbc.sql("select result_json from inbox_command where id = :id")
                .param("id", id).query(String.class).optional();
        if (stored.isPresent() && stored.get() != null) {
            return new Outcome.Answered(Payloads.read(stored.get(), CommandResult.class));
        }
        return new Outcome.InProgress(id.toString());
    }

    private void finish(UUID id, String status, CommandResult result) {
        jdbc.sql("update inbox_command set status = :s, completed_at = :t, result_json = :r where id = :id")
                .param("s", status).param("t", Timestamp.from(clock.instant()))
                .param("r", Payloads.write(result)).param("id", id).update();
    }

    static CommandResult rejected(String commandId, String code) {
        return new CommandResult(ContractVersion.CURRENT, commandId, CommandResult.Status.REJECTED, null, code);
    }

    private static UUID idOf(SignedCommand signed) {
        try {
            return UUID.fromString(signed.command().id());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String truncate(String v, int max) {
        return v == null ? "" : v.length() <= max ? v : v.substring(0, max);
    }
}
