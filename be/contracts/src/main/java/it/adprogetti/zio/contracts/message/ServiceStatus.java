package it.adprogetti.zio.contracts.message;

/** State of one Edge service (database, Ollama, NAS...). {@code detail} is a short code, never a secret. */
public record ServiceStatus(String name, State state, String detail) {
    public enum State { UP, DOWN, DEGRADED, NOT_CONFIGURED }
}
