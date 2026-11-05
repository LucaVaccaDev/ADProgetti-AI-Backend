package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiModelInfo;
import it.adprogetti.zio.contracts.payload.AiModelsResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** What is installed, what is loaded in memory, and who serves which function. */
@Service
public class ModelCatalog {

    private final OllamaClient ollama;
    private final ModelAssignments assignments;

    ModelCatalog(OllamaClient ollama, ModelAssignments assignments) {
        this.ollama = ollama;
        this.assignments = assignments;
    }

    /** @throws AiUnavailableException if Ollama cannot be reached */
    public AiModelsResult snapshot() {
        Map<String, OllamaClient.LoadedModel> loaded = ollama.loaded().stream()
                .collect(Collectors.toMap(OllamaClient.LoadedModel::name, Function.identity(), (a, b) -> a));
        List<AiModelInfo> models = new ArrayList<>();
        for (OllamaClient.InstalledModel m : ollama.installed()) {
            OllamaClient.LoadedModel l = loaded.get(m.name());
            try {
                models.add(new AiModelInfo(m.name(), m.sizeBytes(), l == null ? 0 : l.sizeBytes() /* total memory held by the loaded model */,
                        l != null));
            } catch (IllegalArgumentException e) {
                // a model whose name does not fit the contract (e.g. a path-like name) is not offered
            }
        }
        return new AiModelsResult(models, assignments.effectiveAll());
    }

    public boolean isInstalled(String model) {
        return ollama.installed().stream().anyMatch(m -> m.name().equals(model));
    }
}
