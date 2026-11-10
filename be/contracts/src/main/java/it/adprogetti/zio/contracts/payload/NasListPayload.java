package it.adprogetti.zio.contracts.payload;

/**
 * Payload of {@code nas.list}: a read-only listing of one NAS folder. The access mode is never part of the
 * payload: the Edge always uses INDEX_READ. An empty path lists the allowed roots.
 */
public record NasListPayload(String path, Integer offset, Integer limit) {

    public static final int MAX_PATH_LENGTH = 1024;
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 200;

    public NasListPayload {
        path = path == null ? "" : path;
        if (path.length() > MAX_PATH_LENGTH || path.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalArgumentException("Invalid path");
        }
        offset = offset == null ? 0 : offset;
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset");
        }
        limit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
