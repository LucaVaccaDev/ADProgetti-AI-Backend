package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.edge.secrets.SecretNames;
import it.adprogetti.zio.edge.secrets.SecretStore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Talks to FreeLLMAPI (OpenAI-compatible) over loopback only. The unified key is read from the keychain on each
 * call (a rotation needs no restart) and is never logged. Only {@link ExternalAiService} may use this class.
 * Errors are reduced to short codes.
 */
@Component
class FreeLlmApiClient {

    record Answer(String content, String routedVia, int promptTokens, int completionTokens) {
    }

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final URI base;
    private final Duration requestTimeout;
    private final SecretStore secrets;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    FreeLlmApiClient(FreeLlmApiProperties props, SecretStore secrets) {
        this.secrets = secrets;
        if (props.url().isEmpty()) {
            this.base = null;
        } else {
            URI parsed = URI.create(props.url());
            if (parsed.getHost() == null || !LOOPBACK.contains(parsed.getHost()) || !"http".equals(parsed.getScheme())) {
                throw new IllegalStateException("edge.ai.freellmapi.url must be http on loopback (127.0.0.1/localhost)");
            }
            this.base = parsed;
        }
        this.requestTimeout = props.requestTimeout();
    }

    boolean urlConfigured() {
        return base != null;
    }

    Optional<String> key() {
        return secrets.get(SecretNames.FREELLMAPI_KEY);
    }

    /**
     * One non-streaming chat turn. {@code userTag} is an opaque per-user-and-conversation id (never a name), so
     * FreeLLMAPI's automatic model switching does not mix the contexts of different people.
     */
    Answer chat(String profile, String system, String user, int maxTokens, String userTag) {
        if (base == null) {
            throw new AiUnavailableException("NOT_CONFIGURED", null);
        }
        String key = key().orElseThrow(() -> new AiUnavailableException("NO_KEY", null));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", profile);
        body.put("stream", false);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.3);
        body.put("user", userTag);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/v1/chat/completions")).timeout(requestTimeout)
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new AiRequestException("AUTH");
            }
            if (status == 429) {
                throw new AiUnavailableException("PROVIDER_QUOTA", null);
            }
            if (status >= 400 && status < 500) {
                throw new AiRequestException("BAD_REQUEST");
            }
            if (status != 200) {
                throw new AiUnavailableException("AI_ERROR", null);
            }
            JsonNode root = MAPPER.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asString("");
            if (content.isBlank()) {
                throw new AiUnavailableException("AI_BAD_RESPONSE", null);
            }
            String routedVia = response.headers().firstValue("X-Routed-Via").orElse(root.path("model").asString("unknown"));
            return new Answer(content, shorten(routedVia), root.path("usage").path("prompt_tokens").asInt(),
                    root.path("usage").path("completion_tokens").asInt());
        } catch (IOException e) {
            throw new AiUnavailableException("AI_UNAVAILABLE", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("INTERRUPTED", e);
        } catch (JacksonException e) {
            throw new AiUnavailableException("AI_BAD_RESPONSE", e);
        }
    }

    private static String shorten(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}]", "");
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
