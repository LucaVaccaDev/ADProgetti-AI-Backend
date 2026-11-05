package it.adprogetti.zio.contracts.command;

import java.util.Arrays;
import java.util.Optional;

/**
 * Hub-to-Edge command types allowed in Release 1, each with the permission the requesting user must
 * hold. The Edge refuses any type that is not listed here.
 */
public enum CommandType {
    SYSTEM_PING("system.ping", "SYSTEM_READ"),
    AI_MODELS_LIST("ai.models.list", "SYSTEM_READ"),
    AI_MODEL_TEST("ai.model.test", "SETTINGS_MANAGE"),
    AI_MODEL_ASSIGN("ai.model.assign", "SETTINGS_MANAGE");

    private final String code;
    private final String requiredPermission;

    CommandType(String code, String requiredPermission) {
        this.code = code;
        this.requiredPermission = requiredPermission;
    }

    public String code() {
        return code;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public static Optional<CommandType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
