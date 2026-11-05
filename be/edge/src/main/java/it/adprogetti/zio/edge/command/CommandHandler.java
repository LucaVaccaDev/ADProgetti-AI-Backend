package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;

/** Executes one command type. Returns the result as JSON; throws on failure (the message is never sent). */
public interface CommandHandler {

    CommandType type();

    String handle(HubCommand command, Object payload) throws Exception;
}
