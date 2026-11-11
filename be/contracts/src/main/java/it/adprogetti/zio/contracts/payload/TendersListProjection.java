package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Projection {@code tenders.list}: open tenders found in public sources (TED, Europaconcorsi), as the Hub may keep
 * them (CLAUDE.md section 4: tender monitoring results). Titles, entity names and links only: no document text, no
 * description, no analysis of the studio. Times are ISO-8601 instants (UTC), dates are {@code yyyy-MM-dd}.
 * {@code url} is always https, at most 500 characters; for TED it is built by the Edge from the notice number, for
 * other sources it is the link of the publishing portal and must be treated as untrusted text.
 *
 * <p>The list does not fit one message (the Hub accepts 65,536 characters per projection), so it travels in
 * shards: entity {@code s0}, {@code s1}... hold the tenders (soonest deadline first, {@code shard} = their number),
 * and the entity {@code all} is the index ({@code shard} = {@link #INDEX}): it says how many shards are current
 * ({@code shards}), the total and the state of each source, and carries no tenders. A reader takes the index first
 * and then only the shards it names, so shards left over from a longer list are never read.
 */
public record TendersListProjection(int shard, int shards, int total, List<SourceState> sources, List<Tender> tenders) {

    /** Value of {@code shard} in the index message. */
    public static final int INDEX = -1;
    /** Most tenders in one shard (the Edge also cuts by size). */
    public static final int MAX_TENDERS = 150;

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
