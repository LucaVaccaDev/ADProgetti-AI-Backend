package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.contracts.payload.ReferencesResult;
import it.adprogetti.zio.contracts.payload.ReferencesSummaryResult;
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

    ReferencesResult.Counts counts() {
        return jdbc.sql("select status, count(*) from reference_service group by status").query((rs, n) -> new Object[] {
                rs.getString(1), rs.getInt(2) }).list().stream().collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(o -> (String) o[0], o -> (Integer) o[1]), m -> new ReferencesResult.Counts(
                        m.getOrDefault("DA_APPROVARE", 0), m.getOrDefault("DA_VERIFICARE", 0),
                        m.getOrDefault("APPROVATA", 0), m.getOrDefault("SCARTATA", 0))));
    }

    int count(String status) {
        return jdbc.sql("select count(*) from reference_service where (cast(:s as text) is null or status = :s)")
                .param("s", status).query(Integer.class).single();
    }

    /** Newest first. Amounts are read in one more query for the page. */
    List<ReferencesResult.Item> page(String status, int offset, int limit) {
        List<ReferencesResult.Item> base = jdbc.sql("""
                select id, cig, place, period_text, period_from, period_to, object, client, role_text, share_percent,
                    status, issues from reference_service
                where (cast(:s as text) is null or status = :s)
                order by period_to desc nulls last, id desc offset :o limit :l""")
                .param("s", status).param("o", offset).param("l", limit).query((rs, n) -> new Object[] {
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getBigDecimal(10), rs.getString(11), rs.getString(12) }).list().stream()
                .map(o -> item(o)).toList();
        return base;
    }

    Optional<ReferencesResult.Item> one(long id) {
        return jdbc.sql("""
                select id, cig, place, period_text, period_from, period_to, object, client, role_text, share_percent,
                    status, issues from reference_service where id = :id""").param("id", id)
                .query((rs, n) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getBigDecimal(10), rs.getString(11), rs.getString(12) }).optional().map(o -> item(o));
    }

    private ReferencesResult.Item item(Object[] o) {
        long id = (Long) o[0];
        List<ReferencesResult.Amount> amounts = jdbc.sql(
                "select work_id, category, amount from reference_work_amount where service_id = :id order by work_id")
                .param("id", id).query((rs, n) -> new ReferencesResult.Amount(rs.getString(1), rs.getString(2),
                        rs.getBigDecimal(3).doubleValue())).list();
        double total = amounts.stream().mapToDouble(ReferencesResult.Amount::amount).sum();
        java.math.BigDecimal share = (java.math.BigDecimal) o[9];
        String issues = (String) o[11];
        return new ReferencesResult.Item(id, (String) o[1], (String) o[2], (String) o[3], (String) o[4], (String) o[5],
                (String) o[6], (String) o[7], (String) o[8], share == null ? null : share.doubleValue(), (String) o[10],
                issues == null ? List.of() : List.of(issues.split(",")), amounts, total);
    }

    /** @return the status found, or empty if the reference does not exist; updates only when the move is allowed */
    Optional<String> status(long id) {
        return jdbc.sql("select status from reference_service where id = :id").param("id", id).query(String.class)
                .optional();
    }

    void setStatus(long id, String status) {
        jdbc.sql("update reference_service set status = :s, updated_at = :t where id = :id").param("id", id)
                .param("s", status).param("t", Timestamp.from(clock.instant())).update();
    }

    /** Approved references that ended in [from, asOf], per work id. A service ending after asOf is not finished yet. */
    List<ReferencesSummaryResult.Row> approvedByWorkId(java.time.LocalDate from, java.time.LocalDate asOf) {
        return jdbc.sql("""
                select a.work_id, sum(a.amount), count(distinct s.id)
                from reference_work_amount a join reference_service s on s.id = a.service_id
                where s.status = 'APPROVATA' and s.period_to is not null and s.period_to >= :f and s.period_to <= :t
                group by a.work_id order by a.work_id""").param("f", Date.valueOf(from)).param("t", Date.valueOf(asOf))
                .query((rs, n) -> new ReferencesSummaryResult.Row(rs.getString(1), rs.getBigDecimal(2).doubleValue(),
                        rs.getInt(3))).list();
    }

    int approvedServices(java.time.LocalDate from, java.time.LocalDate asOf) {
        return jdbc.sql("""
                select count(*) from reference_service where status = 'APPROVATA' and period_to is not null
                and period_to >= :f and period_to <= :t""").param("f", Date.valueOf(from))
                .param("t", Date.valueOf(asOf)).query(Integer.class).single();
    }

    int approvedWithoutEndDate() {
        return jdbc.sql("select count(*) from reference_service where status = 'APPROVATA' and period_to is null")
                .query(Integer.class).single();
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
