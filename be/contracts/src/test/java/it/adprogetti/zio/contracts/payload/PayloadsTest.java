package it.adprogetti.zio.contracts.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.contracts.command.CommandType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayloadsTest {

    @Test
    void validPayloadRoundTrips() {
        AiModelAssignPayload original = new AiModelAssignPayload(AiFunction.CLASSIFICATION, "qwen3:8b");
        Object parsed = Payloads.parse(CommandType.AI_MODEL_ASSIGN, Payloads.write(original));
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void unknownFieldIsRejected() {
        assertThatThrownBy(() -> Payloads.parse(CommandType.AI_MODEL_TEST,
                "{\"model\":\"qwen3:8b\",\"function\":\"ANALYSIS\",\"extra\":1}"))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void modelNameWithPathOrOptionsIsRejected() {
        for (String bad : List.of("../../etc/passwd", "qwen3 8b", "a;rm -rf", "", "-x", "m/odel")) {
            String json = "{\"function\":\"ANALYSIS\",\"model\":\"" + bad + "\"}";
            assertThatThrownBy(() -> Payloads.parse(CommandType.AI_MODEL_ASSIGN, json))
                    .as(bad).isInstanceOf(InvalidPayloadException.class);
        }
    }

    @Test
    void unknownFunctionIsRejected() {
        assertThatThrownBy(() -> Payloads.parse(CommandType.AI_MODEL_TEST,
                "{\"model\":\"qwen3:8b\",\"function\":\"HACK\"}")).isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void emptyPayloadIsAcceptedForCommandsWithoutParameters() {
        assertThat(Payloads.parse(CommandType.AI_MODELS_LIST, "")).isInstanceOf(AiModelsListPayload.class);
        assertThat(Payloads.parse(CommandType.SYSTEM_PING, "{}")).isInstanceOf(SystemPingPayload.class);
    }

    @Test
    void malformedJsonIsRejected() {
        assertThatThrownBy(() -> Payloads.parse(CommandType.AI_MODEL_TEST, "{not json"))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void resultRoundTrips() {
        AiModelsResult result = new AiModelsResult(List.of(new AiModelInfo("bge-m3", 1_200_000_000L, 0, false)),
                Map.of(AiFunction.EMBEDDING, "bge-m3"));
        assertThat(Payloads.read(Payloads.write(result), AiModelsResult.class)).isEqualTo(result);
    }
}
