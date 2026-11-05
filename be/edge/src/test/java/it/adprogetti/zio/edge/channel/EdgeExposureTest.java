package it.adprogetti.zio.edge.channel;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/** The Edge must not offer anything to the network (D-02). */
class EdgeExposureTest extends EdgeIntegrationTestBase {

    @Value("${server.address}") String address;
    @Value("${server.port}") int port;

    private int status(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void listensOnLoopbackOnly() {
        assertThat(address).isEqualTo("127.0.0.1");
    }

    @Test
    void onlyTheHealthProbeIsReachable() throws Exception {
        assertThat(status("/actuator/health")).isEqualTo(200);
        assertThat(status("/actuator/env")).isIn(401, 403);
        assertThat(status("/api/anything")).isIn(401, 403);
        assertThat(status("/")).isIn(401, 403);
    }
}
