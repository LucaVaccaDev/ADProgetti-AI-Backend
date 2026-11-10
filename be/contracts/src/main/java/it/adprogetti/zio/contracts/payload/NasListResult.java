package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Result of {@code nas.list}: names and metadata only, never contents. {@code path} is the logical path
 * ({@code share:/folder}), never a path of the Mac. The Hub keeps this result only for a few minutes.
 */
public record NasListResult(String path, List<Entry> entries, int total, boolean truncated) {

    /** @param modifiedAt ISO-8601 UTC; empty for the allowed roots, which are virtual entries */
    public record Entry(String name, boolean directory, long size, String modifiedAt) {
    }

    public NasListResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
