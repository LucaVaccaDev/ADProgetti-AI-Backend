package it.adprogetti.zio.contracts.command;

/** Remembers nonces already used. Implementations must be thread-safe. */
public interface NonceStore {

    /**
     * Records the nonce.
     *
     * @return true if it was new, false if it had already been seen
     */
    boolean markSeen(String nonce, long expiresAtEpochMs);
}
