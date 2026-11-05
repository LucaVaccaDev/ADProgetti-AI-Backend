package it.adprogetti.zio.contracts.message;

import it.adprogetti.zio.contracts.ContractVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Strict encoding and decoding of channel frames. Unknown fields or kinds are errors. */
public final class ChannelCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ChannelCodec() {
    }

    public static String encode(ChannelEnvelope.Kind kind, Object message) {
        return MAPPER.writeValueAsString(
                new ChannelEnvelope(ContractVersion.CURRENT, kind, MAPPER.writeValueAsString(message)));
    }

    public static ChannelEnvelope decodeEnvelope(String frame) {
        try {
            ChannelEnvelope envelope = MAPPER.readValue(frame, ChannelEnvelope.class);
            if (envelope.kind() == null || envelope.body() == null || envelope.schemaVersion() != ContractVersion.CURRENT) {
                throw new InvalidFrameException("Invalid envelope", null);
            }
            return envelope;
        } catch (JacksonException e) {
            throw new InvalidFrameException("Invalid envelope", e);
        }
    }

    public static <T> T decodeBody(ChannelEnvelope envelope, Class<T> type) {
        try {
            return MAPPER.readValue(envelope.body(), type);
        } catch (JacksonException e) {
            throw new InvalidFrameException("Invalid body for " + envelope.kind(), e);
        }
    }

    public static class InvalidFrameException extends RuntimeException {
        public InvalidFrameException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
