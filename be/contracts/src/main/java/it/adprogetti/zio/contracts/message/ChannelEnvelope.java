package it.adprogetti.zio.contracts.message;

/**
 * Every frame on the Hub-Edge WebSocket. {@code body} is the JSON text of the message named by
 * {@code kind}: EdgeHello, EdgeHeartbeat, SignedCommand, CommandAck, CommandResult, ProjectionUpdate.
 */
public record ChannelEnvelope(int schemaVersion, Kind kind, String body) {

    public enum Kind { HELLO, HEARTBEAT, COMMAND, ACK, RESULT, PROJECTION }
}
