package it.adprogetti.zio.edge.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A local HTTP server that speaks enough of the Ollama API for tests; answers are programmable. */
public final class StubOllama {

    public record Reply(int status, String body) {
        public static Reply ok(String body) {
            return new Reply(200, body);
        }
    }

    public record Call(String path, JsonNode body) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public volatile String tags;
    public volatile String ps;
    public volatile Function<JsonNode, Reply> chat;
    public volatile Function<JsonNode, Reply> embed;

    public StubOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        reset();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        tags = "{\"models\":[{\"name\":\"qwen3:8b\",\"size\":5200000000},{\"name\":\"bge-m3:latest\",\"size\":1200000000}]}";
        ps = "{\"models\":[]}";
        chat = body -> Reply.ok(chatBody("{\"category\":\"BUSINESS_RELEVANT\",\"confidence\":0.9}"));
        embed = body -> Reply.ok("{\"embeddings\":[[0.1,0.2,0.3]],\"prompt_eval_count\":2}");
        calls.clear();
    }

    public static String chatBody(String content) {
        return MAPPER.createObjectNode().<tools.jackson.databind.node.ObjectNode>set("message",
                MAPPER.createObjectNode().put("role", "assistant").put("content", content))
                .put("prompt_eval_count", 12).put("eval_count", 6).put("eval_duration", 200_000_000L).toString();
    }

    public List<Call> calls(String path) {
        return calls.stream().filter(c -> c.path().equals(path)).toList();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode body = raw.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        calls.add(new Call(path, body));
        Reply reply = switch (path) {
            case "/api/tags" -> Reply.ok(tags);
            case "/api/ps" -> Reply.ok(ps);
            case "/api/chat" -> chat.apply(body);
            case "/api/embed" -> embed.apply(body);
            case "/api/version" -> Reply.ok("{\"version\":\"stub\"}");
            default -> new Reply(404, "{}");
        };
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(reply.status(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    public void stop() {
        server.stop(0);
    }
}
