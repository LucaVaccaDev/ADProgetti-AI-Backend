package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.AiUsageProjection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Usage metadata only. There is deliberately no column that could hold a prompt, an answer or a file name. */
@Service
public class AiUsageLog {

    private final JdbcClient jdbc;
    private final Clock clock;

    AiUsageLog(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void record(AiFunction function, String model, String requestedBy, String outcome, String reason, long latencyMs,
            int promptTokens, int completionTokens) {
        jdbc.sql("""
                insert into ai_usage_log (occurred_at, function, model, provider, requested_by, outcome, reason,
                                          latency_ms, prompt_tokens, completion_tokens)
                values (:t, :f, :m, 'ollama', :by, :o, :r, :l, :p, :c)
                """).param("t", Timestamp.from(clock.instant())).param("f", function.name()).param("m", model)
                .param("by", requestedBy).param("o", outcome).param("r", reason).param("l", latencyMs)
                .param("p", promptTokens).param("c", completionTokens).update();
    }

    /** Daily totals for the last days, for the {@code ai.usage} projection. */
    public AiUsageProjection summary(Duration window) {
        List<AiUsageProjection.Row> rows = jdbc.sql("""
                select to_char(occurred_at at time zone 'UTC', 'YYYY-MM-DD') as day, function, model, outcome,
                       count(*) as n, sum(latency_ms) as lat, coalesce(sum(prompt_tokens), 0) as pt,
                       coalesce(sum(completion_tokens), 0) as ct
                from ai_usage_log where occurred_at > :since
                group by 1, function, model, outcome order by 1, function, model, outcome
                """).param("since", Timestamp.from(clock.instant().minus(window)))
                .query((rs, i) -> new AiUsageProjection.Row(rs.getString("day"),
                        AiFunction.valueOf(rs.getString("function")), rs.getString("model"), rs.getString("outcome"),
                        rs.getLong("n"), rs.getLong("lat"), rs.getLong("pt"), rs.getLong("ct"))).list();
        return new AiUsageProjection(rows);
    }
}
