package it.adprogetti.zio.contracts.command;

import java.util.List;

/**
 * Command sent from the Hub to the Edge. The payload is a JSON text produced by the sender;
 * it is kept as a string so the signed bytes are exactly what the receiver verifies.
 */
public record HubCommand(
        int schemaVersion,
        String id,
        String type,
        String requestedBy,
        List<String> permissions,
        long issuedAtEpochMs,
        long expiresAtEpochMs,
        String nonce,
        String payload) {

    public HubCommand {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
