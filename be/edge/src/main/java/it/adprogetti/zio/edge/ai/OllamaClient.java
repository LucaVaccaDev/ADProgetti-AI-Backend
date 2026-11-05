package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.edge.config.EdgeProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Talks to the local Ollama over loopback only (D-04: studio data never leaves the Mac). Any other host
 * is refused at startup. Answers are parsed defensively; error details are reduced to short codes.
 */
@Component
public class OllamaClient {

    public record InstalledModel(String name, long sizeBytes) {
    }

    public record LoadedModel(String name, long sizeBytes, long vramBytes) {
    }

    public record ChatResponse(String content, int promptTokens, int completionTokens, double tokensPerSecond) {
    }

    public record EmbedResponse(float[][] vectors, int promptTokens) {
    }

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final URI base;
    private final Duration requestTimeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    OllamaClient(EdgeProperties props) {
        String url = props.ai().ollamaUrl();
        if (url.isEmpty()) {
            this.base = null;
        } else {
            URI parsed = URI.create(url);
            if (parsed.getHost() == null || !LOOPBACK.contains(parsed.getHost()) || !"http".equals(parsed.getScheme())) {
                throw new IllegalStateException("edge.ai.ollama-url must be http on loopback (127.0.0.1/localhost)");
            }
            this.base = parsed;
        }
        this.requestTimeout = props.ai().requestTimeout();
    }

    public boolean configured() {
        return base != null;
    }

    public List<InstalledModel> installed() {
        JsonNode root = get("/api/tags");
        List<InstalledModel> out = new ArrayList<>();
        for (JsonNode m : root.path("models")) {
            out.add(new InstalledModel(m.path("name").asString(), m.path("size").asLong()));
        }
        return out;
    }

    public List<LoadedModel> loaded() {
        JsonNode root = get("/api/ps");
        List<LoadedModel> out = new ArrayList<>();
        for (JsonNode m : root.path("models")) {
            out.add(new LoadedModel(m.path("name").asString(), m.path("size").asLong(), m.path("size_vram").asLong()));
        }
        return out;
    }

    /** One non-streaming chat turn. {@code schemaJson}, if present, constrains the output (structured output). */
    public ChatResponse chat(String model, String system, String user, String schemaJson, int maxTokens) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("keep_alive", "10m");
        if (model.startsWith("qwen3")) {
            body.put("think", false); // no hidden reasoning text in structured answers
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        if (schemaJson != null) {
            body.set("format", MAPPER.readTree(schemaJson));
        }
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0);
        options.put("num_predict", maxTokens);
        JsonNode root = post("/api/chat", body);
        long evalNanos = root.path("eval_duration").asLong();
        int evalCount = root.path("eval_count").asInt();
        double tps = evalNanos > 0 ? evalCount / (evalNanos / 1_000_000_000.0) : 0;
        return new ChatResponse(root.path("message").path("content").asString(), root.path("prompt_eval_count").asInt(),
                evalCount, tps);
    }

    public EmbedResponse embed(String model, List<String> inputs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("keep_alive", "10m");
        ArrayNode in = body.putArray("input");
        inputs.forEach(in::add);
        JsonNode root = post("/api/embed", body);
        JsonNode embeddings = root.path("embeddings");
        float[][] vectors = new float[embeddings.size()][];
        for (int i = 0; i < embeddings.size(); i++) {
            JsonNode v = embeddings.get(i);
            vectors[i] = new float[v.size()];
            for (int j = 0; j < v.size(); j++) {
                vectors[i][j] = (float) v.get(j).asDouble();
            }
        }
        return new EmbedResponse(vectors, root.path("prompt_eval_count").asInt());
    }

    private JsonNode get(String path) {
        return send(HttpRequest.newBuilder(resolve(path)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private JsonNode post(String path, ObjectNode body) {
        return send(HttpRequest.newBuilder(resolve(path)).timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build());
    }

    private URI resolve(String path) {
        if (base == null) {
            throw new AiUnavailableException("NOT_CONFIGURED", null);
        }
        return base.resolve(path);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404) {
                throw new AiRequestException("MODEL_NOT_INSTALLED");
            }
            if (status >= 400 && status < 500) {
                throw new AiRequestException("BAD_REQUEST");
            }
            if (status != 200) {
                throw new AiUnavailableException("AI_ERROR", null);
            }
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new AiUnavailableException("AI_UNAVAILABLE", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("INTERRUPTED", e);
        } catch (JacksonException e) {
            throw new AiUnavailableException("AI_BAD_RESPONSE", e);
        }
    }
}
