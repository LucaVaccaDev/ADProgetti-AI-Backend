package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.edge.secrets.SecretNames;
import it.adprogetti.zio.edge.secrets.SecretStore;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads Europaconcorsi with the studio's own account. One {@link Session} per run: login, then a few list pages, with
 * cookies kept in memory only. Credentials come from the keychain and appear nowhere else (not in logs, not in
 * exceptions). A failed login is never retried, so the account cannot be locked by a loop.
 */
@Component
class EuropaconcorsiClient {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
    private static final int MAX_BODY_CHARS = 5_000_000;
    private static final Pattern TOKEN = Pattern.compile("name=\"authenticity_token\" value=\"([^\"]+)\"");

    private final EuropaconcorsiProperties props;
    private final SecretStore secrets;
    private final URI base;

    EuropaconcorsiClient(EuropaconcorsiProperties props, SecretStore secrets) {
        this.props = props;
        this.secrets = secrets;
        this.base = URI.create(props.baseUrl());
        boolean https = "https".equals(base.getScheme());
        boolean loopbackHttp = "http".equals(base.getScheme()) && base.getHost() != null && LOOPBACK.contains(base.getHost());
        if (base.getHost() == null || !(https || loopbackHttp)) {
            throw new IllegalStateException("edge.tenders.europaconcorsi.base-url must be https (http only on loopback)");
        }
    }

    /** The portal's own page of a tender (path validated by the parser). */
    String detailUrl(String path) {
        return base.resolve(path).toString();
    }

    boolean hasCredentials() {
        return secrets.get(SecretNames.EUROPACONCORSI_USER).isPresent()
                && secrets.get(SecretNames.EUROPACONCORSI_PASSWORD).isPresent();
    }

    Session login() throws TedUnavailableException {
        String user = secrets.get(SecretNames.EUROPACONCORSI_USER).orElse(null);
        String password = secrets.get(SecretNames.EUROPACONCORSI_PASSWORD).orElse(null);
        if (user == null || password == null) {
            throw new TedUnavailableException("EC_NO_CREDENTIALS", false);
        }
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        Session session = new Session(http);
        Response form = session.get("/login");
        Matcher token = TOKEN.matcher(form.body());
        if (form.status() != 200 || !token.find()) {
            throw new TedUnavailableException("EC_BAD_RESPONSE", false);
        }
        String body = "utf8=" + enc("✓") + "&authenticity_token=" + enc(token.group(1)) + "&person%5Bemail%5D=" + enc(user)
                + "&person%5Bpassword%5D=" + enc(password) + "&person%5Bremember_me%5D=false&commit=Accedi";
        Response answer = session.post("/people/login", body);
        boolean redirectedAway = answer.status() / 100 == 3 && !String.valueOf(answer.location()).contains("login");
        boolean stillForm = answer.status() == 200 && EuropaconcorsiParser.isLoginForm(answer.body());
        if (!(redirectedAway || (answer.status() == 200 && !stillForm))) {
            throw new TedUnavailableException("EC_LOGIN_FAILED", false);
        }
        return session;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    record Response(int status, String body, String location) {
    }

    /** Hosts the files attached to a tender may come from: the portal's file server and the portal itself (tests). */
    java.util.Set<String> attachmentHosts() {
        return java.util.Set.of("assets.europaconcorsi.com", base.getHost());
    }

    /** One download: the bytes, or why there are none ({@code tooLarge}, or an error code). */
    record Download(byte[] bytes, boolean tooLarge, String error) {
    }

    /** A logged-in conversation with the portal. */
    final class Session {
        private final HttpClient http;

        private Session(HttpClient http) {
            this.http = http;
        }

        /** One list page. Throws when the session is no longer valid (the portal sends us back to the login). */
        String listPage(String categoryId, int page) throws TedUnavailableException {
            String path = "/search?country_code=" + props.country() + "&category_id=" + enc(categoryId)
                    + "&order=deadline&locale=it&page=" + page;
            Response r = get(path);
            if (r.status() / 100 == 3 || EuropaconcorsiParser.isLoginForm(r.body())) {
                throw new TedUnavailableException("EC_LOGIN_FAILED", false);
            }
            if (r.status() != 200) {
                throw new TedUnavailableException(r.status() == 403 || r.status() == 429 ? "EC_BLOCKED" : "EC_UNAVAILABLE",
                        r.status() >= 500);
            }
            return r.body();
        }

        /** The page of one tender, by its Europaconcorsi id (digits only). */
        String tenderPage(String id) throws TedUnavailableException {
            if (!id.matches("[0-9]{1,9}")) {
                throw new TedUnavailableException("EC_BAD_RESPONSE", false);
            }
            Response r = get("/bandi/" + id);
            if (r.status() / 100 == 3 || EuropaconcorsiParser.isLoginForm(r.body())) {
                throw new TedUnavailableException("EC_LOGIN_FAILED", false);
            }
            if (r.status() != 200) {
                throw new TedUnavailableException(r.status() == 403 || r.status() == 429 ? "EC_BLOCKED" : "EC_UNAVAILABLE",
                        r.status() >= 500);
            }
            return r.body();
        }

        /**
         * Downloads one attached file (https on an allowed host only, no redirects). A file above {@code maxBytes} is
         * not read: it is reported as too large (by Content-Length, or as soon as the cap is passed).
         */
        Download download(String url, long maxBytes) {
            try {
                URI uri = URI.create(url);
                boolean https = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && LOOPBACK.contains(uri.getHost());
                if (!https || uri.getHost() == null || !attachmentHosts().contains(uri.getHost())) {
                    return new Download(null, false, "EC_BAD_LINK");
                }
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofMinutes(3)).GET()
                        .header("User-Agent", "ZioPino-Edge/1").build();
                HttpResponse<java.io.InputStream> r = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (java.io.InputStream in = r.body()) {
                    if (r.statusCode() != 200) {
                        return new Download(null, false, r.statusCode() == 403 || r.statusCode() == 429 ? "EC_BLOCKED" : "EC_UNAVAILABLE");
                    }
                    long declared = r.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (declared > maxBytes) {
                        return new Download(null, true, null);
                    }
                    byte[] data = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
                    return data.length > maxBytes ? new Download(null, true, null) : new Download(data, false, null);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                return new Download(null, false, "EC_TIMEOUT");
            } catch (IOException | IllegalArgumentException e) {
                return new Download(null, false, "EC_UNREACHABLE");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Download(null, false, "INTERRUPTED");
            }
        }

        private Response get(String path) throws TedUnavailableException {
            return send(HttpRequest.newBuilder(base.resolve(path)).timeout(props.timeout()).GET());
        }

        private Response post(String path, String form) throws TedUnavailableException {
            return send(HttpRequest.newBuilder(base.resolve(path)).timeout(props.timeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)));
        }

        private Response send(HttpRequest.Builder builder) throws TedUnavailableException {
            HttpRequest request = builder.header("User-Agent", "ZioPino-Edge/1").header("Accept", "text/html").build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.body().length() > MAX_BODY_CHARS) {
                    throw new TedUnavailableException("EC_TOO_LARGE", false);
                }
                return new Response(response.statusCode(), response.body(),
                        response.headers().firstValue("Location").orElse(null));
            } catch (java.net.http.HttpTimeoutException e) {
                throw new TedUnavailableException("EC_TIMEOUT", true);
            } catch (IOException e) {
                throw new TedUnavailableException("EC_UNREACHABLE", true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TedUnavailableException("INTERRUPTED", false);
            }
        }
    }
}
