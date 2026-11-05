package it.adprogetti.zio.contracts.payload;

/** An installed local model as reported by the Edge. */
public record AiModelInfo(String name, long sizeBytes, long memoryBytes, boolean loaded) {
    public AiModelInfo {
        ModelName.require(name);
        if (sizeBytes < 0 || memoryBytes < 0) {
            throw new IllegalArgumentException("negative size");
        }
    }
}
