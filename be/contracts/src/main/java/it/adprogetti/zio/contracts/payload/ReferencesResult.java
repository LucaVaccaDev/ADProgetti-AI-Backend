package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Result of {@code references.list} (and, with one item, of {@code references.decide}). Client names and amounts of
 * the studio's past services: shown to the requester and erased by the Hub a few minutes later (D-03).
 */
public record ReferencesResult(List<Item> items, Counts counts, int total) {

    /** @param amounts works amount per work id (E.08, S.03...), as in the certificate */
    public record Item(long id, String cig, String place, String periodText, String periodFrom, String periodTo,
            String object, String client, String role, Double sharePercent, String status, List<String> issues,
            List<Amount> amounts, double totalAmount) {
        public Item {
            issues = issues == null ? List.of() : List.copyOf(issues);
            amounts = amounts == null ? List.of() : List.copyOf(amounts);
        }
    }

    public record Amount(String workId, String category, double amount) {
    }

    /** Services per status, over the whole archive (not only the page). */
    public record Counts(int daApprovare, int daVerificare, int approvata, int scartata) {
    }

    public ReferencesResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
