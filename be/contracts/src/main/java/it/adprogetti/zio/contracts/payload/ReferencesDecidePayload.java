package it.adprogetti.zio.contracts.payload;

import java.util.Set;

/**
 * Payload of {@code references.decide}. {@code DA_APPROVARE} reopens a decision. A reference with unreadable data
 * (status DA_VERIFICARE) cannot be approved: the Edge refuses it.
 */
public record ReferencesDecidePayload(long id, String decision) {

    public static final Set<String> DECISIONS = Set.of("APPROVATA", "SCARTATA", "DA_APPROVARE");

    public ReferencesDecidePayload {
        if (id <= 0 || decision == null || !DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("Invalid decision");
        }
    }
}
