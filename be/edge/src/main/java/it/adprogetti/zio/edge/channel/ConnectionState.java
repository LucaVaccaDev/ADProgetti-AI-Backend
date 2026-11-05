package it.adprogetti.zio.edge.channel;

public enum ConnectionState {
    /** No Hub URL, or credential / public key missing in the keychain. */
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    /** Waiting before the next attempt. */
    BACKOFF
}
