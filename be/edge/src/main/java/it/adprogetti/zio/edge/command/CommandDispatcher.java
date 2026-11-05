package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.payload.InvalidPayloadException;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.channel.OutboxService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Hub commands from arrival to result: intake (signature, nonce, inbox), acknowledgement, the Edge's own
 * policy, payload validation, execution and the answer. A small pool runs commands, so a slow one does not block the rest.
 * Error details are never sent to the Hub: only short codes.
 */
@Service
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final CommandIntake intake;
    private final CommandPolicy policy;
    private final OutboxService outbox;
    private final EdgeAudit audit;
    private final Clock clock;
    private final JdbcClient jdbc;
    private final Map<CommandType, CommandHandler> handlers = new EnumMap<>(CommandType.class);
    // A few workers: a slow AI command (loading a large model) must not hold up the others.
    private final ExecutorService worker = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "edge-commands");
        t.setDaemon(true);
        return t;
    });

    CommandDispatcher(CommandIntake intake, CommandPolicy policy, OutboxService outbox, EdgeAudit audit,
            Clock clock, JdbcClient jdbc, List<CommandHandler> handlerBeans) {
        this.intake = intake;
        this.policy = policy;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.jdbc = jdbc;
        handlerBeans.forEach(h -> handlers.put(h.type(), h));
    }

    /** Entry point for a COMMAND frame. Returns quickly: execution happens on the worker thread. */
    public void onCommand(SignedCommand signed) {
        switch (intake.accept(signed)) {
            case CommandIntake.Outcome.Accepted accepted -> {
                outbox.enqueue(ChannelEnvelope.Kind.ACK, ack(accepted.command().id()));
                worker.submit(() -> run(accepted.command()));
            }
            case CommandIntake.Outcome.Answered answered -> {
                outbox.enqueue(ChannelEnvelope.Kind.ACK, ack(answered.result().commandId()));
                outbox.enqueue(ChannelEnvelope.Kind.RESULT, answered.result());
            }
            case CommandIntake.Outcome.InProgress inProgress ->
                    outbox.enqueue(ChannelEnvelope.Kind.ACK, ack(inProgress.commandId()));
            case CommandIntake.Outcome.Invalid invalid -> log.warn("Command without a usable id ignored");
        }
    }

    private void run(HubCommand command) {
        CommandResult result = execute(command);
        intake.complete(command.id(), result);
        audit.record("COMMAND_EXECUTED", command.type() + " " + command.id(),
                result.status() == CommandResult.Status.SUCCEEDED ? EdgeAudit.SUCCESS : EdgeAudit.FAILURE,
                result.errorCode());
        outbox.enqueue(ChannelEnvelope.Kind.RESULT, result);
    }

    /** Never throws: every outcome, including bugs, becomes a result with a short code. */
    CommandResult execute(HubCommand command) {
        CommandPolicy.Decision decision = policy.check(command);
        if (!decision.allowed()) {
            return CommandIntake.rejected(command.id(), decision.rejection());
        }
        CommandHandler handler = handlers.get(decision.type());
        if (handler == null) {
            return CommandIntake.rejected(command.id(), "UNSUPPORTED");
        }
        try {
            Object payload = Payloads.parse(decision.type(), command.payload());
            String json = handler.handle(command, payload);
            return new CommandResult(ContractVersion.CURRENT, command.id(), CommandResult.Status.SUCCEEDED, json, null);
        } catch (InvalidPayloadException e) {
            return CommandIntake.rejected(command.id(), "INVALID_PAYLOAD");
        } catch (CommandFailure e) {
            return new CommandResult(ContractVersion.CURRENT, command.id(), CommandResult.Status.FAILED, null,
                    e.code());
        } catch (Exception e) {
            log.error("Command {} failed", command.id(), e);
            return new CommandResult(ContractVersion.CURRENT, command.id(), CommandResult.Status.FAILED, null,
                    "EXECUTION_ERROR");
        }
    }

    /**
     * A command received but not finished when the Edge stopped is not run again (it may have had side
     * effects): it is closed as FAILED/INTERRUPTED and the Hub is told.
     */
    @EventListener(ApplicationReadyEvent.class)
    void recoverInterrupted() {
        List<UUID> ids = jdbc.sql("select id from inbox_command where status = 'RECEIVED'")
                .query(UUID.class).list();
        for (UUID id : ids) {
            CommandResult result = new CommandResult(ContractVersion.CURRENT, id.toString(),
                    CommandResult.Status.FAILED, null, "INTERRUPTED");
            intake.complete(id.toString(), result);
            outbox.enqueue(ChannelEnvelope.Kind.RESULT, result);
            audit.record("COMMAND_INTERRUPTED", id.toString(), EdgeAudit.FAILURE, null);
        }
    }

    private CommandAck ack(String commandId) {
        return new CommandAck(ContractVersion.CURRENT, commandId, clock.millis());
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }
}
