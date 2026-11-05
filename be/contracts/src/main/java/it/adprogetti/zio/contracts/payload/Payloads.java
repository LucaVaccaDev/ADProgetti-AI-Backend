package it.adprogetti.zio.contracts.payload;

import it.adprogetti.zio.contracts.command.CommandType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict (de)serialization of command payloads and results. Unknown fields are rejected, and every
 * record validates itself in its constructor: invalid input never reaches the executor.
 */
public final class Payloads {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Payloads() {
    }

    public static Class<?> payloadClass(CommandType type) {
        return switch (type) {
            case SYSTEM_PING -> SystemPingPayload.class;
            case AI_MODELS_LIST -> AiModelsListPayload.class;
            case AI_MODEL_TEST -> AiModelTestPayload.class;
            case AI_MODEL_ASSIGN -> AiModelAssignPayload.class;
        };
    }

    public static Object parse(CommandType type, String json) {
        return read(json == null || json.isBlank() ? "{}" : json, payloadClass(type));
    }

    public static <T> T read(String json, Class<T> target) {
        try {
            return MAPPER.readValue(json, target);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidPayloadException("Invalid payload for " + target.getSimpleName(), e);
        }
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
