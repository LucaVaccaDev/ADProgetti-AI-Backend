package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.DocsSearchResult;
import it.adprogetti.zio.edge.ai.AiGateway;
import it.adprogetti.zio.edge.ai.AiOutcome;
import it.adprogetti.zio.edge.ai.Priority;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Hybrid search: the passages closest in meaning (embedding, pgvector) and those that contain the words (Italian
 * full text), merged by reciprocal rank fusion. If the embedding model is not available the text search still
 * answers and the result says so. The query is only data for the search: it is never given to a model as an instruction.
 */
@Service
public class DocumentSearch {

    private static final int CANDIDATES = 40;
    private static final int RRF_K = 60;
    static final int SNIPPET_CHARS = 240;

    private final JdbcClient jdbc;
    private final AiGateway ai;
    private final DocumentStore store;
    private final DocsProperties props;

    DocumentSearch(JdbcClient jdbc, AiGateway ai, DocumentStore store, DocsProperties props) {
        this.props = props;
        this.jdbc = jdbc;
        this.ai = ai;
        this.store = store;
    }

    public DocsSearchResult search(String query, int limit, String requestedBy) {
        DocsSearchResult.Stats stats = store.stats();
        if (query == null || query.isBlank()) {
            return new DocsSearchResult(List.of(), true, stats);
        }
        List<Long> byText = jdbc.sql("""
                select c.id from document_chunk c join indexed_document d on d.id = c.document_id
                where d.status = 'INDEXED' and c.tsv @@ websearch_to_tsquery('italian', :q)
                order by ts_rank_cd(c.tsv, websearch_to_tsquery('italian', :q)) desc limit :n
                """).param("q", query).param("n", CANDIDATES).query(Long.class).list();
        List<Long> byMeaning = List.of();
        boolean semantic = false;
        String queryVector = null;
        AiOutcome<float[][]> embedded = ai.embed(List.of(query), Priority.HIGH, requestedBy);
        if (embedded.ok() && embedded.value().length == 1 && embedded.value()[0].length == 1024) {
            semantic = true;
            queryVector = DocumentStore.vectorLiteral(embedded.value()[0]);
            byMeaning = jdbc.sql("""
                    select c.id from document_embedding e join document_chunk c on c.id = e.chunk_id
                    join indexed_document d on d.id = c.document_id
                    where d.status = 'INDEXED' and 1 - (e.embedding <=> cast(:v as vector)) >= :min
                    order by e.embedding <=> cast(:v as vector) limit :n
                    """).param("v", queryVector).param("min", props.minSimilarity()).param("n", CANDIDATES)
                    .query(Long.class).list();
        }
        Map<Long, Double> fused = new LinkedHashMap<>();
        fuse(fused, byText);
        fuse(fused, byMeaning);
        List<Long> ranked = fused.entrySet().stream().sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<DocsSearchResult.Hit> hits = new ArrayList<>();
        Set<String> seenDocuments = new HashSet<>();
        for (Long chunkId : ranked) {
            if (hits.size() >= limit) {
                break;
            }
            var row = jdbc.sql("""
                    select d.path, c.page_from, c.page_to, c.content from document_chunk c
                    join indexed_document d on d.id = c.document_id where c.id = :id
                    """).param("id", chunkId).query((rs, i) -> new Object[] { rs.getString("path"),
                            rs.getInt("page_from"), rs.getInt("page_to"), rs.getString("content") }).optional();
            if (row.isEmpty() || !seenDocuments.add((String) row.get()[0])) {
                continue; // one hit per document: the best passage
            }
            hits.add(new DocsSearchResult.Hit((String) row.get()[0], (int) row.get()[1], (int) row.get()[2],
                    snippet((String) row.get()[3]), similarity(chunkId, queryVector)));
        }
        return new DocsSearchResult(hits, semantic, stats);
    }

    /** Cosine similarity of the passage to the query (0 to 1); 0 when the semantic search was not available. */
    private double similarity(long chunkId, String queryVector) {
        if (queryVector == null) {
            return 0;
        }
        Double sim = jdbc.sql("select 1 - (embedding <=> cast(:v as vector)) from document_embedding where chunk_id = :id")
                .param("v", queryVector).param("id", chunkId).query(Double.class).optional().orElse(0.0);
        return Math.max(0, Math.min(1, Math.round(sim * 100) / 100.0));
    }

    private static void fuse(Map<Long, Double> scores, List<Long> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            scores.merge(ranking.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    /** First {@value #SNIPPET_CHARS} characters, cut at a word boundary, on one line. */
    static String snippet(String content) {
        String flat = content.replaceAll("\\s+", " ").strip();
        if (flat.length() <= SNIPPET_CHARS) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', SNIPPET_CHARS);
        return flat.substring(0, cut > SNIPPET_CHARS / 2 ? cut : SNIPPET_CHARS) + "…";
    }
}
