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
    AI_MODEL_ASSIGN("ai.model.assign", "SETTINGS_MANAGE"),
    AI_FREELLMAPI_TEST("ai.freellmapi.test", "SETTINGS_MANAGE"),
    JOBS_RUN("jobs.run", "SETTINGS_MANAGE"),
    JOBS_CANCEL("jobs.cancel", "SETTINGS_MANAGE"),
    /** Read-only listing of NAS folders. The result holds file names, so the Hub keeps it only briefly. */
    NAS_LIST("nas.list", "SETTINGS_MANAGE", true),
    /** Search in the document index. The result holds document excerpts, so the Hub keeps it only briefly. */
    DOCS_SEARCH("docs.search", "SETTINGS_MANAGE", true);

    private final String code;
    private final String requiredPermission;
    private final boolean ephemeralResult;

    CommandType(String code, String requiredPermission) {
        this(code, requiredPermission, false);
    }

    CommandType(String code, String requiredPermission, boolean ephemeralResult) {
        this.code = code;
        this.requiredPermission = requiredPermission;
        this.ephemeralResult = ephemeralResult;
    }

    /** True when the Hub must not keep the result: it is shown to the requester and then erased. */
    public boolean ephemeralResult() {
        return ephemeralResult;
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
