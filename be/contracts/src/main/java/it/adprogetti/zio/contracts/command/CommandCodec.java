package it.adprogetti.zio.contracts.command;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** Deterministic serialization of commands: the signed bytes are the JSON in field declaration order. */
public final class CommandCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CommandCodec() {
    }

    public static byte[] signingBytes(HubCommand command) {
        return MAPPER.writeValueAsString(command).getBytes(StandardCharsets.UTF_8);
    }

    public static String toJson(SignedCommand signed) {
        return MAPPER.writeValueAsString(signed);
    }

    public static SignedCommand fromJson(String json) {
        return MAPPER.readValue(json, SignedCommand.class);
    }
}
