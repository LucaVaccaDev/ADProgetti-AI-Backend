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

    @Test
    void jobsPayloadsAreValidatedAndRoundTrip() {
        JobsRunPayload run = new JobsRunPayload("SYSTEM_SELFCHECK");
        assertThat(Payloads.parse(CommandType.JOBS_RUN, Payloads.write(run))).isEqualTo(run);
        String id = java.util.UUID.randomUUID().toString();
        JobsCancelPayload cancel = new JobsCancelPayload(id);
        assertThat(Payloads.parse(CommandType.JOBS_CANCEL, Payloads.write(cancel))).isEqualTo(cancel);
        for (String bad : List.of("{\"type\":\"lower\"}", "{\"type\":\"../X\"}", "{\"type\":\"\"}",
                "{\"type\":\"SYSTEM_SELFCHECK\",\"extra\":1}", "{}")) {
            assertThatThrownBy(() -> Payloads.parse(CommandType.JOBS_RUN, bad)).as(bad)
                    .isInstanceOf(InvalidPayloadException.class);
        }
        for (String bad : List.of("{\"jobId\":\"not-a-uuid\"}", "{\"jobId\":\"\"}", "{}")) {
            assertThatThrownBy(() -> Payloads.parse(CommandType.JOBS_CANCEL, bad)).as(bad)
                    .isInstanceOf(InvalidPayloadException.class);
        }
    }

    @Test
    void jobsSummaryRoundTripsWithoutAnyPayloadOrResultField() {
        JobsSummaryProjection summary = new JobsSummaryProjection(new JobsSummaryProjection.Counts(1, 0, 5, 1),
                List.of(new JobsSummaryProjection.Job("id", "SYSTEM_SELFCHECK", "SUCCEEDED", 1, 1,
                        "2026-11-05T10:00:00Z", "2026-11-05T10:00:01Z", "2026-11-05T10:00:02Z", null, "admin", false)),
                List.of(new JobsSummaryProjection.Schedule("protocol-run", "PROTOCOL_RUN", false, "Europe/Rome",
                        List.of("14:30", "18:30"), null)));
        String json = Payloads.write(summary);
        assertThat(Payloads.read(json, JobsSummaryProjection.class)).isEqualTo(summary);
        assertThat(json).doesNotContain("payload").doesNotContain("result");
    }

    @Test
    void nasListPayloadIsBoundedAndNeverCarriesAMode() {
        assertThat(Payloads.parse(CommandType.NAS_LIST, "")).isEqualTo(new NasListPayload("", 0, 100));
        assertThat(new NasListPayload("gare:/GARE 2025", 10, 5000).limit()).isEqualTo(NasListPayload.MAX_LIMIT);
        assertThatThrownBy(() -> Payloads.parse(CommandType.NAS_LIST, "{\"path\":\"a\",\"mode\":\"TENDER_WRITE\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.NAS_LIST, "{\"offset\":-1}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.NAS_LIST, "{\"path\":\"a\\u0000b\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.NAS_LIST, "{\"path\":\"" + "x".repeat(1025) + "\"}"))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void nasListResultRoundTripsAndOnlyNasListIsEphemeral() {
        NasListResult result = new NasListResult("gare:/GARE 2025",
                List.of(new NasListResult.Entry("Bando n°1 #2.pdf", false, 1234, "2025-03-01T10:00:00Z")), 1, false);
        assertThat(Payloads.read(Payloads.write(result), NasListResult.class)).isEqualTo(result);
        assertThat(CommandType.NAS_LIST.ephemeralResult()).isTrue();
        assertThat(CommandType.AI_MODELS_LIST.ephemeralResult()).isFalse();
    }

    @Test
    void externalAiTestCarriesNoParametersAndRejectsExtraFields() {
        assertThat(Payloads.parse(CommandType.AI_FREELLMAPI_TEST, "")).isInstanceOf(AiExternalTestPayload.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.AI_FREELLMAPI_TEST, "{\"prompt\":\"hello\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        AiExternalTestResult result = new AiExternalTestResult(true, "groq/llama", 850, null);
        assertThat(Payloads.read(Payloads.write(result), AiExternalTestResult.class)).isEqualTo(result);
    }

    @Test
    void docsSearchPayloadIsBoundedAndEphemeral() {
        assertThat(Payloads.parse(CommandType.DOCS_SEARCH, "")).isEqualTo(new DocsSearchPayload("", null));
        assertThat(new DocsSearchPayload("  copriferro  ", 500)).isEqualTo(new DocsSearchPayload("copriferro", 20));
        assertThatThrownBy(() -> Payloads.parse(CommandType.DOCS_SEARCH, "{\"query\":\"" + "x".repeat(301) + "\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.DOCS_SEARCH, "{\"query\":\"a\\nb\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> Payloads.parse(CommandType.DOCS_SEARCH, "{\"query\":\"a\",\"path\":\"gare:/\"}"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThat(CommandType.DOCS_SEARCH.ephemeralResult()).isTrue();
        DocsSearchResult result = new DocsSearchResult(List.of(new DocsSearchResult.Hit("gare:/GARE 2025/a.pdf", 2, 3,
                "…", 0.8)), true, new DocsSearchResult.Stats(10, 8, 1, 1, 0, 0, 120, "2026-11-10T10:00:00Z", "COMPLETED"));
        assertThat(Payloads.read(Payloads.write(result), DocsSearchResult.class)).isEqualTo(result);
    }
}
