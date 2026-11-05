package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.contracts.payload.SystemPingPayload;
import it.adprogetti.zio.contracts.payload.SystemPingResult;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
class SystemPingHandler implements CommandHandler {

    private final Clock clock;

    SystemPingHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CommandType type() {
        return CommandType.SYSTEM_PING;
    }

    @Override
    public String handle(HubCommand command, Object payload) {
        return Payloads.write(new SystemPingResult(((SystemPingPayload) payload).label(), clock.millis()));
    }
}
