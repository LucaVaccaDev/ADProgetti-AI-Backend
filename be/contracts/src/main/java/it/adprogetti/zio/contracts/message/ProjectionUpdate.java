package it.adprogetti.zio.contracts.message;

/** Read-only projection pushed by the Edge. Only data allowed on the Hub (CLAUDE.md section 4). */
public record ProjectionUpdate(int schemaVersion, String idempotencyKey, String projection, String entityId,
        long version, String data) {
}
