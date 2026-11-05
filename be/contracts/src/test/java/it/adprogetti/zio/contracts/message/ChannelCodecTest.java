package it.adprogetti.zio.contracts.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.contracts.ContractVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelCodecTest {

    @Test
    void heartbeatRoundTrips() {
        EdgeHeartbeat beat = new EdgeHeartbeat(ContractVersion.CURRENT, "0.1.0", 1000L, 42L,
                List.of(new ServiceStatus("ollama", ServiceStatus.State.UP, null)));
        ChannelEnvelope envelope = ChannelCodec.decodeEnvelope(ChannelCodec.encode(ChannelEnvelope.Kind.HEARTBEAT, beat));
        assertThat(envelope.kind()).isEqualTo(ChannelEnvelope.Kind.HEARTBEAT);
        assertThat(ChannelCodec.decodeBody(envelope, EdgeHeartbeat.class)).isEqualTo(beat);
    }

    @Test
    void unknownKindUnknownFieldAndWrongVersionAreRejected() {
        for (String bad : List.of(
                "{\"schemaVersion\":1,\"kind\":\"DELETE_ALL\",\"body\":\"{}\"}",
                "{\"schemaVersion\":1,\"kind\":\"ACK\",\"body\":\"{}\",\"x\":1}",
                "{\"schemaVersion\":9,\"kind\":\"ACK\",\"body\":\"{}\"}",
                "{\"schemaVersion\":1,\"kind\":\"ACK\"}",
                "not json")) {
            assertThatThrownBy(() -> ChannelCodec.decodeEnvelope(bad)).as(bad)
                    .isInstanceOf(ChannelCodec.InvalidFrameException.class);
        }
    }
}
