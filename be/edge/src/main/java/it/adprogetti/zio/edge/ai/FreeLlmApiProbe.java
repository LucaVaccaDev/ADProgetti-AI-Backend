package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.edge.health.ServiceProbe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Is FreeLLMAPI up, and does it accept our key? Loopback only; no key means NOT_CONFIGURED (not an error). */
@Component
class FreeLlmApiProbe implements ServiceProbe {

    private static final String NAME = "freellmapi";

    private final FreeLlmApiProperties props;
    private final FreeLlmApiClient client;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    FreeLlmApiProbe(FreeLlmApiProperties props, FreeLlmApiClient client) {
        this.props = props;
        this.client = client;
    }

    @Override
    public ServiceStatus probe() {
        if (!client.urlConfigured()) {
            return new ServiceStatus(NAME, ServiceStatus.State.NOT_CONFIGURED, null);
        }
        var key = client.key();
        if (key.isEmpty()) {
            return new ServiceStatus(NAME, ServiceStatus.State.NOT_CONFIGURED, "NO_KEY");
        }
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(props.url() + "/v1/models"))
                    .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + key.get()).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 200) {
                return new ServiceStatus(NAME, ServiceStatus.State.UP, null);
            }
            return new ServiceStatus(NAME, ServiceStatus.State.DEGRADED,
                    status == 401 || status == 403 ? "KEY_REJECTED" : "HTTP_" + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ServiceStatus(NAME, ServiceStatus.State.DOWN, "INTERRUPTED");
        } catch (Exception e) {
            return new ServiceStatus(NAME, ServiceStatus.State.DOWN, "UNREACHABLE");
        }
    }
}
