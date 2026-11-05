package it.adprogetti.zio.contracts.message;

/** The Edge confirms it has stored a command (at-least-once delivery); execution is reported separately. */
public record CommandAck(int schemaVersion, String commandId, long receivedAtEpochMs) {
}
