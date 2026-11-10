package it.adprogetti.zio.contracts.payload;

/**
 * Payload of {@code docs.search}. An empty query returns only the state of the index. The query is a question
 * or keywords, never a command: the Edge uses it only as data for the search.
 */
public record DocsSearchPayload(String query, Integer limit) {

    public static final int MAX_QUERY_LENGTH = 300;
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 20;

    public DocsSearchPayload {
        query = query == null ? "" : query.strip();
        if (query.length() > MAX_QUERY_LENGTH || query.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalArgumentException("Invalid query");
        }
        limit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
