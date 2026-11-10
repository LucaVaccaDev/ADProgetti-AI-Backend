package it.adprogetti.zio.contracts.payload;

/**
 * Result of {@code ai.freellmapi.test}. {@code routedVia} is the provider/model that served the test
 * (header {@code X-Routed-Via}); {@code error} is a short code, never free text.
 */
public record AiExternalTestResult(boolean ok, String routedVia, long latencyMs, String error) {
}
