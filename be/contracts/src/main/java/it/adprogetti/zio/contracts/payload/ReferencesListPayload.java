package it.adprogetti.zio.contracts.payload;

import java.util.Set;

/** Payload of {@code references.list}: optional status filter and a page. */
public record ReferencesListPayload(String status, Integer offset, Integer limit) {

    public static final Set<String> STATUSES = Set.of("DA_APPROVARE", "DA_VERIFICARE", "APPROVATA", "SCARTATA");
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public ReferencesListPayload {
        status = status == null || status.isBlank() ? null : status;
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
        offset = offset == null || offset < 0 ? 0 : offset;
        limit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
