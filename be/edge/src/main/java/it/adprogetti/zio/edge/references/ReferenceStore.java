package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.edge.references.ReferenceSheetParser.Amount;
import it.adprogetti.zio.edge.references.ReferenceSheetParser.Service;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Database access of the references. Only parameterised queries. */
@Repository
class ReferenceStore {

    record Existing(long id, String status, String contentHash) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    ReferenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Optional<Existing> find(String key) {
        return jdbc.sql("select id, status, content_hash from reference_service where ref_key = :k").param("k", key)
                .query((rs, n) -> new Existing(rs.getLong(1), rs.getString(2), rs.getString(3))).optional();
    }

    long insert(String key, Service s, String status, String hash, String file, String sheet) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.sql("""
                insert into reference_service (ref_key, cig, place, period_text, period_from, period_to, object, client,
                    role_text, share_percent, approval_ref, status, issues, content_hash, source_file, source_sheet,
                    source_column, imported_at, updated_at)
                values (:k, :cig, :place, :pt, :pf, :pto, :obj, :cl, :role, :share, :appr, :st, :iss, :h, :file, :sheet,
                    :col, :now, :now) returning id""")
                .param("k", key).param("cig", s.cig()).param("place", cut(s.place(), 200))
                .param("pt", cut(s.periodText(), 100)).param("pf", date(s.periodFrom())).param("pto", date(s.periodTo()))
                .param("obj", s.object()).param("cl", cut(s.client(), 200)).param("role", cut(s.roleText(), 100))
                .param("share", s.sharePercent()).param("appr", s.approvalRef()).param("st", status)
                .param("iss", issues(s)).param("h", hash).param("file", file).param("sheet", sheet)
                .param("col", s.column()).param("now", now).query(Long.class).single();
    }

    void update(long id, Service s, String status, String hash, String file, String sheet) {
        jdbc.sql("""
                update reference_service set cig = :cig, place = :place, period_text = :pt, period_from = :pf,
                    period_to = :pto, object = :obj, client = :cl, role_text = :role, share_percent = :share,
                    approval_ref = :appr, status = :st, issues = :iss, content_hash = :h, source_file = :file,
                    source_sheet = :sheet, source_column = :col, updated_at = :now where id = :id""")
                .param("id", id).param("cig", s.cig()).param("place", cut(s.place(), 200))
                .param("pt", cut(s.periodText(), 100)).param("pf", date(s.periodFrom())).param("pto", date(s.periodTo()))
                .param("obj", s.object()).param("cl", cut(s.client(), 200)).param("role", cut(s.roleText(), 100))
                .param("share", s.sharePercent()).param("appr", s.approvalRef()).param("st", status)
                .param("iss", issues(s)).param("h", hash).param("file", file).param("sheet", sheet)
                .param("col", s.column()).param("now", Timestamp.from(clock.instant())).update();
    }

    void replaceAmounts(long serviceId, List<Amount> amounts) {
        jdbc.sql("delete from reference_work_amount where service_id = :id").param("id", serviceId).update();
        for (Amount a : amounts) {
            jdbc.sql("""
                    insert into reference_work_amount (service_id, work_id, category, complexity, amount)
                    values (:id, :w, :c, :x, :a)
                    on conflict (service_id, work_id) do update set amount = reference_work_amount.amount + excluded.amount""")
                    .param("id", serviceId).param("w", a.workId()).param("c", cut(a.category(), 12))
                    .param("x", a.complexity()).param("a", a.amount()).update();
        }
    }

    static String hash(Service s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.cig()).append('|').append(s.place()).append('|').append(s.periodText()).append('|')
                .append(s.object()).append('|').append(s.client()).append('|').append(s.roleText()).append('|')
                .append(s.sharePercent()).append('|').append(s.approvalRef());
        s.amounts().stream().sorted(java.util.Comparator.comparing(Amount::workId)).forEach(a -> sb.append('|')
                .append(a.workId()).append('=').append(a.amount()).append('/').append(a.complexity()));
        return sha(sb.toString());
    }

    static String sha(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String issues(Service s) {
        return s.issues().isEmpty() ? null : String.join(",", s.issues());
    }

    private static Date date(java.time.LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
