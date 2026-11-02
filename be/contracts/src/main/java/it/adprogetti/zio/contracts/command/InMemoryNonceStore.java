package it.adprogetti.zio.contracts.command;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Simple in-memory store, for tests. Production uses the persistent inbox table on the Edge. */
public final class InMemoryNonceStore implements NonceStore {

    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>();

    @Override
    public boolean markSeen(String nonce, long expiresAtEpochMs) {
        return seen.putIfAbsent(nonce, expiresAtEpochMs) == null;
    }
}
