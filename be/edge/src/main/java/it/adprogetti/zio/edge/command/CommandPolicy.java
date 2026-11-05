package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The Edge's own invariant rules, applied even to commands the Hub signed (CLAUDE.md section 5).
 * Today: the command type must be known and carry the permission the Hub verified for it. Rules for
 * PEC read-only, NAS write paths and email sending are added with the modules that need them.
 */
@Component
class CommandPolicy {

    static final String UNKNOWN_TYPE = "UNKNOWN_TYPE";
    static final String POLICY = "POLICY";

    /** @return the command type if allowed, or a rejection code */
    Decision check(HubCommand command) {
        Optional<CommandType> type = CommandType.fromCode(command.type());
        if (type.isEmpty()) {
            return new Decision(null, UNKNOWN_TYPE);
        }
        if (command.requestedBy() == null || command.requestedBy().isBlank()
                || !command.permissions().contains(type.get().requiredPermission())) {
            return new Decision(null, POLICY);
        }
        return new Decision(type.get(), null);
    }

    record Decision(CommandType type, String rejection) {
        boolean allowed() {
            return rejection == null;
        }
    }
}
