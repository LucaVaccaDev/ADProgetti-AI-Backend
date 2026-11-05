package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.edge.config.EdgeProperties;
import it.adprogetti.zio.edge.health.ServiceProbe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Is the local Ollama answering? Only loopback is ever contacted (D-04). */
@Component
class OllamaProbe implements ServiceProbe {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    OllamaProbe(EdgeProperties props) {
        this.baseUrl = props.ai().ollamaUrl();
    }

    @Override
    public ServiceStatus probe() {
        if (baseUrl.isEmpty()) {
            return new ServiceStatus("ollama", ServiceStatus.State.NOT_CONFIGURED, null);
        }
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/version"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    ? new ServiceStatus("ollama", ServiceStatus.State.UP, null)
                    : new ServiceStatus("ollama", ServiceStatus.State.DEGRADED, "HTTP_" + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ServiceStatus("ollama", ServiceStatus.State.DOWN, "INTERRUPTED");
        } catch (Exception e) {
            return new ServiceStatus("ollama", ServiceStatus.State.DOWN, "UNREACHABLE");
        }
    }
}
