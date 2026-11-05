package it.adprogetti.zio.contracts.payload;

/** Payload of {@code ai.model.test}: quick test of an installed model for a function. */
public record AiModelTestPayload(String model, AiFunction function) {
    public AiModelTestPayload {
        ModelName.require(model);
        if (function == null) {
            throw new IllegalArgumentException("function required");
        }
    }
}
