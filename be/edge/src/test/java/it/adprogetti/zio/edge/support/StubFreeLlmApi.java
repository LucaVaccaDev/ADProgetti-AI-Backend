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

/** A local HTTP server that speaks enough of FreeLLMAPI (OpenAI-compatible) for tests; answers are programmable. */
public final class StubFreeLlmApi {

    public record Reply(int status, String body, String routedVia) {
        public static Reply ok(String content) {
            return new Reply(200, chatBody(content), "groq/llama-3.3-70b");
        }

        public static Reply status(int status) {
            return new Reply(status, "{\"error\":{\"message\":\"x\"}}", null);
        }
    }

    public record Call(String path, String authorization, JsonNode body) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public volatile String expectedKey = "freellmapi-test-key";
    public volatile Function<JsonNode, Reply> chat;

    public StubFreeLlmApi() throws IOException {
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
        chat = body -> Reply.ok("Risposta di prova.");
        calls.clear();
    }

    public static String chatBody(String content) {
        return MAPPER.createObjectNode()
                .<tools.jackson.databind.node.ObjectNode>set("choices", MAPPER.createArrayNode().add(MAPPER.createObjectNode()
                        .set("message", MAPPER.createObjectNode().put("role", "assistant").put("content", content))))
                .<tools.jackson.databind.node.ObjectNode>set("usage",
                        MAPPER.createObjectNode().put("prompt_tokens", 20).put("completion_tokens", 7))
                .toString();
    }

    public List<Call> calls(String path) {
        return calls.stream().filter(c -> c.path().equals(path)).toList();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode body = raw.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        calls.add(new Call(path, auth, body));
        Reply reply;
        if (!("Bearer " + expectedKey).equals(auth)) {
            reply = Reply.status(401);
        } else if ("/v1/models".equals(path)) {
            reply = new Reply(200, "{\"data\":[]}", null);
        } else if ("/v1/chat/completions".equals(path)) {
            reply = chat.apply(body);
        } else {
            reply = Reply.status(404);
        }
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        if (reply.routedVia() != null) {
            ex.getResponseHeaders().add("X-Routed-Via", reply.routedVia());
        }
        ex.sendResponseHeaders(reply.status(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    public void stop() {
        server.stop(0);
    }
}
