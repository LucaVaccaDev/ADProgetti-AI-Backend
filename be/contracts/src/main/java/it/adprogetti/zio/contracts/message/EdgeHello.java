package it.adprogetti.zio.contracts.message;

/** First message the Edge sends after connecting. */
public record EdgeHello(int schemaVersion, String edgeVersion, long sentAtEpochMs) {
}
