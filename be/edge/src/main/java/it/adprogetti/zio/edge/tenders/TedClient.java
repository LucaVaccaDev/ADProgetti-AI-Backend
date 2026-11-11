package it.adprogetti.zio.edge.tenders;

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
import tools.jackson.databind.node.ObjectNode;

/**
 * Read-only client of TED's public search API (POST /v3/notices/search, no key). One request at a time, with a
 * timeout, a generic User-Agent (no name, no address of the studio) and no cookies. Errors become short codes.
 */
@Component
class TedClient {

    record Page(List<JsonNode> notices, int total) {
    }

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_BODY_CHARS = 8_000_000;

    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient http;

    TedClient(TedProperties props) {
        URI base = URI.create(props.apiUrl());
        boolean https = "https".equals(base.getScheme());
        boolean loopbackHttp = "http".equals(base.getScheme()) && base.getHost() != null && LOOPBACK.contains(base.getHost());
        if (base.getHost() == null || !(https || loopbackHttp)) {
            throw new IllegalStateException("edge.tenders.api-url must be https (http only on loopback)");
        }
        this.endpoint = base.resolve("/v3/notices/search");
        this.timeout = props.timeout();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    Page search(String query, int page, int limit) throws TedUnavailableException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("query", query);
        var fields = body.putArray("fields");
        for (String f : TedMapper.FIELDS) {
            fields.add(f);
        }
        body.put("page", page);
        body.put("limit", limit);
        body.put("scope", "ALL");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                .header("Accept", "application/json").header("User-Agent", "ZioPino-Edge/1")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                throw new TedUnavailableException("TED_RATE_LIMIT", true);
            }
            if (status >= 500) {
                throw new TedUnavailableException("TED_UNAVAILABLE", true);
            }
            if (status != 200) {
                throw new TedUnavailableException("TED_REJECTED", false);
            }
            if (response.body().length() > MAX_BODY_CHARS) {
                throw new TedUnavailableException("TED_TOO_LARGE", false);
            }
            JsonNode root = MAPPER.readTree(response.body());
            List<JsonNode> notices = new ArrayList<>();
            JsonNode array = root.get("notices");
            if (array == null || !array.isArray()) {
                throw new TedUnavailableException("TED_BAD_RESPONSE", false);
            }
            array.forEach(notices::add);
            JsonNode total = root.get("totalNoticeCount");
            return new Page(notices, total != null && total.isNumber() ? total.asInt() : notices.size());
        } catch (TedUnavailableException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TedUnavailableException("TED_TIMEOUT", true);
        } catch (IOException e) {
            throw new TedUnavailableException("TED_UNREACHABLE", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TedUnavailableException("INTERRUPTED", false);
        } catch (JacksonException e) {
            throw new TedUnavailableException("TED_BAD_RESPONSE", false);
        }
    }
}
