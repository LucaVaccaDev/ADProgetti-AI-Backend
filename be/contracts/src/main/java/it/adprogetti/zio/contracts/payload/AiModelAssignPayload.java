package it.adprogetti.zio.contracts.payload;

/** Payload of {@code ai.model.assign}: assign an installed model to a function. */
public record AiModelAssignPayload(AiFunction function, String model) {
    public AiModelAssignPayload {
        if (function == null) {
            throw new IllegalArgumentException("function required");
        }
        ModelName.require(model);
    }
}
