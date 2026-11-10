package it.adprogetti.zio.edge.docs;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Document index settings (edge.docs.*). {@code indexRoots} are logical paths ({@code share:/folder}); empty means
 * every root allowed for INDEX_READ. Whatever is listed here, the NAS service still refuses paths outside the
 * INDEX_READ roots. {@code maxFilesPerRun} 0 means no limit. {@code minSimilarity} (cosine, 0 = off) drops passages that
 * match only by meaning and not closely enough: it depends on the embedding model and must be set from real data.
 */
@ConfigurationProperties("edge.docs")
public record DocsProperties(List<String> indexRoots, int maxFilesPerRun, long maxFileBytes, int maxPages,
        int maxChars, Duration extractTimeout, int chunkSize, int chunkOverlap, int embedBatch, double minSimilarity) {

    public DocsProperties {
        indexRoots = indexRoots == null ? List.of() : List.copyOf(indexRoots);
        maxFilesPerRun = Math.max(0, maxFilesPerRun);
        maxFileBytes = maxFileBytes <= 0 ? 100L * 1024 * 1024 : maxFileBytes;
        maxPages = maxPages <= 0 ? 3000 : maxPages;
        maxChars = maxChars <= 0 ? 2_000_000 : maxChars;
        extractTimeout = extractTimeout == null ? Duration.ofSeconds(60) : extractTimeout;
        chunkSize = chunkSize < 200 ? 800 : chunkSize;
        chunkOverlap = chunkOverlap < 0 || chunkOverlap >= chunkSize / 2 ? 100 : chunkOverlap;
        embedBatch = embedBatch <= 0 ? 16 : embedBatch;
        minSimilarity = minSimilarity < 0 || minSimilarity >= 1 ? 0 : minSimilarity;
    }
}
