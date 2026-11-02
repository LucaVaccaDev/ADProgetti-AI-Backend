package it.adprogetti.zio.contracts.message;

/** Outcome of a command, sent from the Edge to the Hub. Result is JSON text; never contains secrets. */
public record CommandResult(int schemaVersion, String commandId, Status status, String result, String errorCode) {

    public enum Status { SUCCEEDED, FAILED, REJECTED }
}
