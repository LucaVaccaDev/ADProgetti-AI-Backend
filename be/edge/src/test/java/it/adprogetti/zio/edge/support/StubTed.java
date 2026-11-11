package it.adprogetti.zio.edge.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local HTTP server that speaks enough of TED's search API for tests: tests never reach the real service. */
public final class StubTed {

    public record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    public volatile Reply reply;

    public StubTed() throws IOException {
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
        reply = new Reply(200, "{\"notices\":[],\"totalNoticeCount\":0,\"iterationNextToken\":null,\"timedOut\":false}");
        requests.clear();
    }

    /** The JSON bodies received, in order. */
    public List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange ex) throws IOException {
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " "
                + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Reply r = reply;
        byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(r.status(), out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }
}
