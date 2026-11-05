package it.adprogetti.zio.edge.ai;

/**
 * Result of an AI call. {@code DA_VERIFICARE} means the model answered but the answer did not pass
 * validation: it is never repaired or guessed (CLAUDE.md section 8). {@code UNAVAILABLE} means no answer.
 */
public record AiOutcome<T>(Status status, T value, String reason, String model, long latencyMs, int promptTokens,
        int completionTokens, double tokensPerSecond) {

    public enum Status { OK, DA_VERIFICARE, UNAVAILABLE }

    public boolean ok() {
        return status == Status.OK;
    }

    static <T> AiOutcome<T> unavailable(String reason, String model, long latencyMs) {
        return new AiOutcome<>(Status.UNAVAILABLE, null, reason, model, latencyMs, 0, 0, 0);
    }
}
