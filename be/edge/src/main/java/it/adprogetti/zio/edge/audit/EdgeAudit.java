package it.adprogetti.zio.edge.audit;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Local audit of what the Edge does on the Hub's behalf. Own transaction: it survives a rollback. */
@Service
public class EdgeAudit {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    private final JdbcClient jdbc;
    private final Clock clock;

    EdgeAudit(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String target, String outcome, String detail) {
        jdbc.sql("insert into audit_log (occurred_at, action, target, outcome, detail) values (:t, :a, :g, :o, :d)")
                .param("t", Timestamp.from(clock.instant())).param("a", action)
                .param("g", truncate(target, 200)).param("o", outcome).param("d", truncate(detail, 500)).update();
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
