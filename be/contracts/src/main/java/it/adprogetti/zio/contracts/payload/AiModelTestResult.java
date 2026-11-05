package it.adprogetti.zio.contracts.payload;

/** Result of {@code ai.model.test}. {@code error} is a short code, never provider output. */
public record AiModelTestResult(String model, AiFunction function, boolean ok, long latencyMs,
        double tokensPerSecond, String error) {
}
