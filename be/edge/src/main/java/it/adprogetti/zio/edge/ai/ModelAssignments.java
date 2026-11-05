package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.AiModelAssignResult;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.config.EdgeProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Which model serves which function: the administrator's choice, otherwise the hardware profile's default. */
@Service
public class ModelAssignments {

    private final JdbcClient jdbc;
    private final EdgeProperties props;
    private final Clock clock;
    private final EdgeAudit audit;

    ModelAssignments(JdbcClient jdbc, EdgeProperties props, Clock clock, EdgeAudit audit) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
    }

    public Optional<String> effective(AiFunction function) {
        Optional<String> chosen = jdbc.sql("select model from ai_model_assignment where function = :f")
                .param("f", function.name()).query(String.class).optional();
        return chosen.isPresent() ? chosen : Optional.ofNullable(props.ai().defaultModels().get(function));
    }

    public Map<AiFunction, String> effectiveAll() {
        Map<AiFunction, String> all = new EnumMap<>(AiFunction.class);
        for (AiFunction f : AiFunction.values()) {
            effective(f).ifPresent(m -> all.put(f, m));
        }
        return all;
    }

    /** Assigns a model; the previous one is kept so the change can be undone by assigning it back. */
    public AiModelAssignResult assign(AiFunction function, String model, String requestedBy) {
        String previous = effective(function).orElse(null);
        jdbc.sql("""
                insert into ai_model_assignment (function, model, previous_model, assigned_by, assigned_at)
                values (:f, :m, :p, :by, :t)
                on conflict (function) do update set model = :m, previous_model = :p, assigned_by = :by, assigned_at = :t
                """).param("f", function.name()).param("m", model).param("p", previous).param("by", requestedBy)
                .param("t", Timestamp.from(clock.instant())).update();
        audit.record("AI_MODEL_ASSIGNED", function + " -> " + model, EdgeAudit.SUCCESS, "previous=" + previous);
        return new AiModelAssignResult(function, model, previous);
    }
}
