package it.adprogetti.zio.contracts.payload;

/** Result of {@code ai.model.assign}: the previous model is kept so the change can be undone. */
public record AiModelAssignResult(AiFunction function, String model, String previousModel) {
}
