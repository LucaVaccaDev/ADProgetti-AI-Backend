package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only projections pushed by the Edge. Only whitelisted names are accepted (data boundary,
 * CLAUDE.md section 4): anything else is dropped. Older versions never overwrite newer ones.
 */
@Service
public class ProjectionService {

    /** Projections allowed in Release 1. New ones are added here together with their review. */
    static final Set<String> ALLOWED = Set.of("system.status", "ai.models");
    static final int MAX_DATA_CHARS = 65_536;
    private static final Pattern ENTITY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public record ProjectionView(String data, Instant updatedAt) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    ProjectionService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true if applied, false if refused or already known. */
    @Transactional
    boolean apply(ProjectionUpdate update) {
        if (update.projection() == null || !ALLOWED.contains(update.projection())
                || update.entityId() == null || !ENTITY.matcher(update.entityId()).matches()
                || update.idempotencyKey() == null || update.idempotencyKey().isBlank()
                || update.idempotencyKey().length() > 100 || update.version() < 0
                || update.data() == null || update.data().length() > MAX_DATA_CHARS || !isJson(update.data())) {
            return false;
        }
        Timestamp now = Timestamp.from(clock.instant());
        int fresh = jdbc.sql("""
                insert into channel_inbox (idempotency_key, received_at) values (:k, :t)
                on conflict (idempotency_key) do nothing
                """).param("k", update.idempotencyKey()).param("t", now).update();
        if (fresh == 0) {
            return false;
        }
        jdbc.sql("""
                insert into projection (name, entity_id, version, data, updated_at)
                values (:n, :e, :v, :d, :t)
                on conflict (name, entity_id) do update
                   set version = :v, data = :d, updated_at = :t where projection.version < :v
                """).param("n", update.projection()).param("e", update.entityId()).param("v", update.version())
                .param("d", update.data()).param("t", now).update();
        return true;
    }

    public Optional<ProjectionView> find(String name, String entityId) {
        return jdbc.sql("select data, updated_at from projection where name = :n and entity_id = :e")
                .param("n", name).param("e", entityId)
                .query((rs, i) -> new ProjectionView(rs.getString("data"), rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    private static boolean isJson(String text) {
        try {
            MAPPER.readTree(text);
            return true;
        } catch (JacksonException e) {
            return false;
        }
    }
}
