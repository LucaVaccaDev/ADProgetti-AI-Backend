package it.adprogetti.zio.contracts.payload;

import java.util.List;
import java.util.Map;

/** Result of {@code ai.models.list}: installed models and current function assignments. */
public record AiModelsResult(List<AiModelInfo> models, Map<AiFunction, String> assignments) {
    public AiModelsResult {
        models = models == null ? List.of() : List.copyOf(models);
        assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
    }
}
