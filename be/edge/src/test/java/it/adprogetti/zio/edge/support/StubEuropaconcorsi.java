package it.adprogetti.zio.edge.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A local server that behaves like the Europaconcorsi login and search: a form with a token, a cookie after a good
 * login, and list pages that need that cookie. Tests never reach the real site.
 */
public final class StubEuropaconcorsi {

    public static final String USER = "studio@example.test";
    public static final String PASSWORD = "stub-password";

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    /** Page body by request path and query (e.g. {@code /search?country_code=it&category_id=200025&...&page=1}). */
    public volatile Function<String, String> page;
    /** Page of one tender ({@code /bandi/<id>}, needs the login) by path. */
    public volatile Function<String, String> tenderPage;
    /** Attached files served at {@code /files/<name>} (public, like the portal's file server), by name. */
    public final java.util.Map<String, byte[]> files = new java.util.concurrent.ConcurrentHashMap<>();
    /** Content-Length announced for a file name instead of its real size (to test the size cap). */
    public final java.util.Map<String, Long> announcedSize = new java.util.concurrent.ConcurrentHashMap<>();
    /** When true every list request is answered with a redirect to the login. */
    public volatile boolean sessionExpires;
    /** When true the login is refused whatever the credentials. */
    public volatile boolean rejectLogin;

    public StubEuropaconcorsi() throws IOException {
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
        page = path -> "<html><body><div class=\"competitions\"></div></body></html>";
        tenderPage = path -> "<html><body>nessun allegato</body></html>";
        files.clear();
        announcedSize.clear();
        sessionExpires = false;
        rejectLogin = false;
        requests.clear();
    }

    /** "METHOD path?query" of every request, without bodies (a login body holds the password). */
    public List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getRawPath();
        String query = ex.getRequestURI().getRawQuery();
        String full = path + (query == null ? "" : "?" + query);
        requests.add(ex.getRequestMethod() + " " + full);
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean loggedIn = String.valueOf(ex.getRequestHeaders().getFirst("Cookie")).contains("session=ok");
        if (path.equals("/login")) {
            send(ex, 200, "<form action=\"/people/login\" method=\"post\"><input type=\"hidden\" name=\"authenticity_token\""
                    + " value=\"tok123\" /><input name=\"person[email]\" /><input type=\"password\" name=\"person[password]\" /></form>", null);
        } else if (path.equals("/people/login")) {
            String expected = "person%5Bemail%5D=" + java.net.URLEncoder.encode(USER, StandardCharsets.UTF_8)
                    + "&person%5Bpassword%5D=" + java.net.URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
            if (!rejectLogin && body.contains("authenticity_token=tok123") && body.contains(expected)) {
                ex.getResponseHeaders().add("Set-Cookie", "session=ok; Path=/; HttpOnly");
                ex.getResponseHeaders().add("Location", "/");
                send(ex, 302, "", null);
            } else {
                send(ex, 200, "<form><input type=\"password\" name=\"person[password]\" /></form>", null);
            }
        } else if (path.equals("/search")) {
            if (!loggedIn || sessionExpires) {
                ex.getResponseHeaders().add("Location", "/login");
                send(ex, 302, "", null);
            } else {
                send(ex, 200, page.apply(URLDecoder.decode(full, StandardCharsets.UTF_8)), null);
            }
        } else if (path.startsWith("/bandi/")) {
            if (!loggedIn || sessionExpires) {
                ex.getResponseHeaders().add("Location", "/login");
                send(ex, 302, "", null);
            } else {
                send(ex, 200, tenderPage.apply(path), null);
            }
        } else if (path.startsWith("/files/")) {
            byte[] data = files.get(path.substring("/files/".length()));
            if (data == null) {
                send(ex, 404, "", null);
            } else {
                ex.getResponseHeaders().add("Content-Type", "application/pdf");
                long length = announcedSize.getOrDefault(path.substring("/files/".length()), (long) data.length);
                ex.sendResponseHeaders(200, length);
                if (length == data.length) {
                    ex.getResponseBody().write(data);
                }
                ex.close();
            }
        } else {
            send(ex, 404, "", null);
        }
    }

    private static void send(HttpExchange ex, int status, String body, String type) throws IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type == null ? "text/html; charset=utf-8" : type);
        ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }
}
