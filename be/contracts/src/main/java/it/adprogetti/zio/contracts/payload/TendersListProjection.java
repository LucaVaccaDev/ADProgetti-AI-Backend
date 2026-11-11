package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Projection {@code tenders.list}: open tenders found in public sources (TED, Europaconcorsi), as the Hub may keep
 * them (CLAUDE.md section 4: tender monitoring results). Titles, entity names and links only: no document text, no
 * description, no analysis of the studio. Times are ISO-8601 instants (UTC), dates are {@code yyyy-MM-dd}.
 * {@code url} is always https, at most 500 characters; for TED it is built by the Edge from the notice number, for
 * other sources it is the link of the publishing portal and must be treated as untrusted text.
 */
public record TendersListProjection(int total, List<SourceState> sources, List<Tender> tenders) {

    public static final int MAX_TENDERS = 200;

    public TendersListProjection {
        sources = sources == null ? List.of() : List.copyOf(sources);
        tenders = tenders == null ? List.of() : List.copyOf(tenders.size() > MAX_TENDERS ? tenders.subList(0, MAX_TENDERS) : tenders);
    }

    /** State of one source: {@code name} is TED or EUROPACONCORSI, {@code status} OK, FAILED, RUNNING or NEVER. */
    public record SourceState(String name, String syncedAt, String status, String errorCode, int open) {
    }

    public record Tender(String id, String source, String title, String buyer, Double amount, String currency,
            String deadline, String published, String procedure, List<String> cpv, String place, int lots, String url) {
        public Tender {
            cpv = cpv == null ? List.of() : List.copyOf(cpv);
        }
    }
}
