package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Result of {@code references.summary}: for each window (3, 5, 10 years back from {@code asOf}) the amount of works of
 * the APPROVED references per work id. A reference counts in a window when its service ended inside it. Shown to the
 * requester and erased by the Hub (D-03).
 */
public record ReferencesSummaryResult(String asOf, List<Window> windows, int approvedWithoutEndDate) {

    public record Window(int years, String from, List<Row> rows, double total, int services) {
        public Window {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record Row(String workId, double amount, int services) {
    }

    public ReferencesSummaryResult {
        windows = windows == null ? List.of() : List.copyOf(windows);
    }
}
