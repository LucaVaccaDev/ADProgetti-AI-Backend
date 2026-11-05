package it.adprogetti.zio.edge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.edge.config.EdgeProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

    private OllamaClient client(String url) {
        return new OllamaClient(new EdgeProperties("t", null, null,
                new EdgeProperties.Ai(url, "test", 2, 1, null, null)));
    }

    @Test
    void onlyLoopbackOverHttpIsAccepted() {
        for (String bad : List.of("http://192.168.1.57:11434", "http://example.com", "https://127.0.0.1:11434",
                "http://10.0.0.5:11434", "file:///etc/passwd", "http://127.0.0.1.evil.com:11434")) {
            assertThatThrownBy(() -> client(bad)).as(bad).isInstanceOf(IllegalStateException.class);
        }
        assertThat(client("http://127.0.0.1:11434").configured()).isTrue();
        assertThat(client("http://localhost:11434").configured()).isTrue();
    }

    @Test
    void blankUrlMeansNotConfiguredAndCallsReportItHonestly() {
        OllamaClient c = client("");
        assertThat(c.configured()).isFalse();
        assertThatThrownBy(c::installed).isInstanceOf(AiUnavailableException.class)
                .extracting(e -> ((AiUnavailableException) e).code()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void anUnreachableServiceIsReportedAsUnavailable() {
        OllamaClient c = client("http://127.0.0.1:1"); // nothing listens on port 1
        assertThatThrownBy(c::installed).isInstanceOf(AiUnavailableException.class);
    }
}
