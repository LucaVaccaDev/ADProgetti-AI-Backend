package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Projection {@code tenders.list}: open tenders found in public sources (today only TED), as the Hub may keep them
 * (CLAUDE.md section 4: tender monitoring results). Public data only: no document text, no analysis of the studio.
 * Times are ISO-8601 instants (UTC), dates are {@code yyyy-MM-dd}. {@code url} is built by the Edge from the
 * notice number, never copied from the source.
 */
public record TendersListProjection(String source, String syncedAt, String lastStatus, int total, List<Tender> tenders) {

    public static final int MAX_TENDERS = 200;

    public TendersListProjection {
        tenders = tenders == null ? List.of() : List.copyOf(tenders.size() > MAX_TENDERS ? tenders.subList(0, MAX_TENDERS) : tenders);
    }

    public record Tender(String id, String title, String buyer, Double amount, String currency, String deadline,
            String published, String procedure, List<String> cpv, String place, int lots, String url) {
        public Tender {
            cpv = cpv == null ? List.of() : List.copyOf(cpv);
        }
    }
}
