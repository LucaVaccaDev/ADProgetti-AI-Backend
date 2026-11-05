package it.adprogetti.zio.contracts.payload;

/** Result of {@code system.ping}. */
public record SystemPingResult(String label, long edgeTimeEpochMs) {
}
