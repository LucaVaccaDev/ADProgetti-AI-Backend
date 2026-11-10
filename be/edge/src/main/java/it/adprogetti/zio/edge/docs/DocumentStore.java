package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.payload.DocsSearchResult;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Database access of the document index. Only parameterised queries. Document text lives only here (D-03). */
@Repository
class DocumentStore {

    record Known(long id, long sizeBytes, Instant modifiedAt, String status) {
    }

    record Counts(int scanned, int indexed, int skipped, int noText, int failed, int duplicates, int tooLarge,
            int removed, int unsupported, int chunks) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    DocumentStore(JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    long startRun() {
        return jdbc.sql("insert into indexing_run (started_at, status) values (:t, 'RUNNING') returning id")
                .param("t", Timestamp.from(clock.instant())).query(Long.class).single();
    }

    void finishRun(long runId, String status, String errorCode, Counts c) {
        jdbc.sql("""
                update indexing_run set finished_at = :t, status = :s, error_code = :e, scanned = :sc, indexed = :i,
                    skipped = :sk, no_text = :nt, failed = :f, duplicates = :d, too_large = :tl, removed = :r,
                    unsupported = :u, chunks = :ch where id = :id
                """).param("t", Timestamp.from(clock.instant())).param("s", status).param("e", errorCode)
                .param("sc", c.scanned()).param("i", c.indexed()).param("sk", c.skipped()).param("nt", c.noText())
                .param("f", c.failed()).param("d", c.duplicates()).param("tl", c.tooLarge()).param("r", c.removed())
                .param("u", c.unsupported()).param("ch", c.chunks()).param("id", runId).update();
    }

    /** A run left RUNNING by a crash is closed, so the page never shows a run that cannot finish. */
    void closeStaleRuns() {
        jdbc.sql("update indexing_run set status = 'FAILED', error_code = 'INTERRUPTED', finished_at = :t "
                + "where status = 'RUNNING'").param("t", Timestamp.from(clock.instant())).update();
    }

    Optional<Known> find(String path) {
        return jdbc.sql("select id, size_bytes, modified_at, status from indexed_document where path = :p")
                .param("p", path).query((rs, n) -> new Known(rs.getLong("id"), rs.getLong("size_bytes"),
                        rs.getTimestamp("modified_at").toInstant(), rs.getString("status"))).optional();
    }

    void markSeen(long id, long runId) {
        jdbc.sql("update indexed_document set seen_run = :r where id = :id").param("r", runId).param("id", id).update();
    }

    Optional<Long> indexedWithSameContent(String sha256, String exceptPath) {
        return jdbc.sql("select id from indexed_document where sha256 = :s and status = 'INDEXED' and path <> :p "
                + "order by id limit 1").param("s", sha256).param("p", exceptPath).query(Long.class).optional();
    }

    /** Documents under the given roots not seen in this run: the file is gone from the NAS. */
    int markRemoved(List<String> roots, long runId) {
        int total = 0;
        for (String root : roots) {
            String folder = root.endsWith("/") ? root : root + "/";
            total += jdbc.sql("""
                    update indexed_document set status = 'REMOVED', indexed_at = :t
                    where status <> 'REMOVED' and seen_run is distinct from :r
                      and (path = :root or starts_with(path, :folder))
                    """).param("t", Timestamp.from(clock.instant())).param("r", runId).param("root", root)
                    .param("folder", folder).update();
            jdbc.sql("""
                    delete from document_chunk where document_id in (select id from indexed_document
                    where status = 'REMOVED' and (path = :root or starts_with(path, :folder)))
                    """).param("root", root).param("folder", folder).update();
        }
        return total;
    }

    /** A file without searchable passages (scan, failure, too large, duplicate): only the outcome is kept. */
    void saveWithoutText(String path, long size, Instant modified, String sha256, String status, String errorCode,
            Integer pages, Integer chars, Long duplicateOf, long runId) {
        tx.executeWithoutResult(s -> {
            long id = upsert(path, size, modified, sha256, status, errorCode, pages, chars, duplicateOf, runId);
            jdbc.sql("delete from document_chunk where document_id = :id").param("id", id).update();
        });
    }

    void saveIndexed(String path, long size, Instant modified, String sha256, int pages, int chars,
            List<Chunker.Chunk> chunks, float[][] vectors, long runId) {
        tx.executeWithoutResult(s -> {
            long id = upsert(path, size, modified, sha256, "INDEXED", null, pages, chars, null, runId);
            jdbc.sql("delete from document_chunk where document_id = :id").param("id", id).update();
            for (int i = 0; i < chunks.size(); i++) {
                Chunker.Chunk c = chunks.get(i);
                long chunkId = jdbc.sql("""
                        insert into document_chunk (document_id, ordinal, page_from, page_to, content)
                        values (:d, :o, :pf, :pt, :c) returning id
                        """).param("d", id).param("o", c.ordinal()).param("pf", c.pageFrom()).param("pt", c.pageTo())
                        .param("c", c.content()).query(Long.class).single();
                jdbc.sql("insert into document_embedding (chunk_id, embedding) values (:c, cast(:v as vector))")
                        .param("c", chunkId).param("v", vectorLiteral(vectors[i])).update();
            }
        });
    }

    private long upsert(String path, long size, Instant modified, String sha256, String status, String errorCode,
            Integer pages, Integer chars, Long duplicateOf, long runId) {
        return jdbc.sql("""
                insert into indexed_document (path, size_bytes, modified_at, sha256, status, error_code, pages,
                    text_chars, duplicate_of, indexed_at, seen_run)
                values (:p, :sz, :m, :sha, :st, :e, :pg, :ch, :dup, :t, :r)
                on conflict (path) do update set size_bytes = excluded.size_bytes, modified_at = excluded.modified_at,
                    sha256 = excluded.sha256, status = excluded.status, error_code = excluded.error_code,
                    pages = excluded.pages, text_chars = excluded.text_chars, duplicate_of = excluded.duplicate_of,
                    indexed_at = excluded.indexed_at, seen_run = excluded.seen_run
                returning id
                """).param("p", path).param("sz", size).param("m", Timestamp.from(modified)).param("sha", sha256)
                .param("st", status).param("e", errorCode).param("pg", pages).param("ch", chars)
                .param("dup", duplicateOf).param("t", Timestamp.from(clock.instant())).param("r", runId)
                .query(Long.class).single();
    }

    DocsSearchResult.Stats stats() {
        record Row(String status, int n) {
        }
        List<Row> rows = jdbc.sql("select status, count(*) as n from indexed_document group by status")
                .query((rs, i) -> new Row(rs.getString("status"), rs.getInt("n"))).list();
        java.util.function.ToIntFunction<String> count = s -> rows.stream().filter(r -> r.status().equals(s))
                .mapToInt(Row::n).sum();
        int chunks = jdbc.sql("select count(*) from document_chunk").query(Integer.class).single();
        var last = jdbc.sql("select started_at, status from indexing_run order by id desc limit 1")
                .query((rs, i) -> new String[] { rs.getTimestamp("started_at").toInstant().toString(),
                        rs.getString("status") }).optional();
        int documents = rows.stream().filter(r -> !r.status().equals("REMOVED")).mapToInt(Row::n).sum();
        return new DocsSearchResult.Stats(documents, count.applyAsInt("INDEXED"), count.applyAsInt("NO_TEXT"),
                count.applyAsInt("FAILED"), count.applyAsInt("DUPLICATE"), count.applyAsInt("TOO_LARGE"), chunks,
                last.map(l -> l[0]).orElse(null), last.map(l -> l[1]).orElse(null));
    }

    static String vectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
