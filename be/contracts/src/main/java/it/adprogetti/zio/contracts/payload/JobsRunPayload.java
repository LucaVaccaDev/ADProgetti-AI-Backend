package it.adprogetti.zio.contracts.payload;

import java.util.regex.Pattern;

/** Payload of {@code jobs.run}: start a job of an allowed type now. The Edge decides which types are allowed. */
public record JobsRunPayload(String type) {

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{2,39}");

    public JobsRunPayload {
        if (type == null || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Invalid job type");
        }
    }
}
