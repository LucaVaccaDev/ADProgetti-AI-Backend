package it.adprogetti.zio.contracts.payload;

/** Payload of {@code system.ping}: no data, only a free label echoed back for diagnostics. */
public record SystemPingPayload(String label) {
    public SystemPingPayload {
        if (label != null && label.length() > 64) {
            throw new IllegalArgumentException("label too long");
        }
    }
}
