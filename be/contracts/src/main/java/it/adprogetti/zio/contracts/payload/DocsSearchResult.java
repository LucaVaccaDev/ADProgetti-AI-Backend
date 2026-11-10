package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Result of {@code docs.search}: where a passage is and a short excerpt. It is shown to the requester and the Hub
 * erases it a few minutes later (D-03): the Hub never keeps document text. {@code semantic} is false when only the
 * text search could run (the embedding model was not available).
 */
public record DocsSearchResult(List<Hit> hits, boolean semantic, Stats stats) {

    /**
     * @param path logical path ({@code share:/folder/file.pdf}); {@code snippet} at most 240 characters;
     *             {@code score} is the cosine similarity of the best passage (0 to 1), or 0 when only the text search ran
     */
    public record Hit(String path, int pageFrom, int pageTo, String snippet, double score) {
    }

    /** State of the document index: counts only. */
    public record Stats(int documents, int indexed, int noText, int failed, int duplicates, int tooLarge, int chunks,
            String lastRunAt, String lastRunStatus) {
    }

    public DocsSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
