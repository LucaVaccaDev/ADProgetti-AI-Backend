package it.adprogetti.zio.hub.tenders;

import it.adprogetti.zio.hub.audit.AuditService;
import it.adprogetti.zio.hub.channel.ProjectionService;
import it.adprogetti.zio.hub.common.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Accept ("partecipiamo") or discard a monitored tender, for the whole studio. Discarded tenders can be recovered; every
 * decision disappears at the tender's deadline. The deadline comes from the list the Edge sent, so a client cannot choose it.
 */
@Service
public class TenderDecisionService {

    public enum Kind { ACCEPTED, REJECTED }

    public record Decision(String value, String by, Instant at) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_SHARDS = 20;

    private final JdbcClient jdbc;
    private final ProjectionService projections;
    private final AuditService audit;
    private final Clock clock;

    TenderDecisionService(JdbcClient jdbc, ProjectionService projections, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.projections = projections;
        this.audit = audit;
        this.clock = clock;
    }

    /** Decisions still in force, by tender id (those past their deadline are never returned, even before the purge). */
    @Transactional(readOnly = true)
    public Map<String, Decision> current() {
        Map<String, Decision> out = new LinkedHashMap<>();
        jdbc.sql("select tender_id, decision, decided_name, decided_at from tender_decision "
                + "where deadline_at is null or deadline_at > :now").param("now", Timestamp.from(clock.instant()))
                .query((rs, i) -> {
                    out.put(rs.getString(1), new Decision(rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()));
                    return null;
                }).list();
        return out;
    }

    @Transactional
    public Decision decide(String tenderId, Kind kind, UUID actorId, String actorName, String ip) {
        Tender tender = find(tenderId);
        Instant now = clock.instant();
        if (tender.deadline() != null && !tender.deadline().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "La gara è già scaduta.");
        }
        jdbc.sql("""
                insert into tender_decision (tender_id, decision, decided_by, decided_name, decided_at, deadline_at)
                values (:id, :d, :u, :n, :t, :dl)
                on conflict (tender_id) do update set decision = :d, decided_by = :u, decided_name = :n, decided_at = :t,
                    deadline_at = :dl
                """).param("id", tenderId).param("d", kind.name()).param("u", actorId).param("n", actorName)
                .param("t", Timestamp.from(now)).param("dl", tender.deadline() == null ? null : Timestamp.from(tender.deadline()))
                .update();
        audit.record(kind == Kind.ACCEPTED ? "TENDER_ACCEPTED" : "TENDER_REJECTED", actorId, actorName, tenderId,
                AuditService.SUCCESS, null, ip);
        return new Decision(kind.name(), actorName, now);
    }

    /** Takes a decision back: an accepted tender becomes "to verify" again, a discarded one is recovered. */
    @Transactional
    public void clear(String tenderId, UUID actorId, String actorName, String ip) {
        int removed = jdbc.sql("delete from tender_decision where tender_id = :id").param("id", tenderId).update();
        if (removed > 0) {
            audit.record("TENDER_DECISION_CLEARED", actorId, actorName, tenderId, AuditService.SUCCESS, null, ip);
        }
    }

    /** Past the deadline a tender is deleted everywhere, and so is its decision. */
    @Scheduled(fixedDelayString = "${hub.tenders.purge-interval:1h}", initialDelayString = "${hub.tenders.purge-interval:1h}")
    @Transactional
    public int purgeExpired() {
        return jdbc.sql("delete from tender_decision where deadline_at is not null and deadline_at <= :now")
                .param("now", Timestamp.from(clock.instant())).update();
    }

    private record Tender(Instant deadline) {
    }

    /** Looks the tender up in the list the Edge sent (index, then the shards it names). */
    private Tender find(String tenderId) {
        var index = projections.find("tenders.list", "all");
        if (index.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Gara non trovata.");
        }
        int shards = Math.max(0, Math.min(MAPPER.readTree(index.get().data()).path("shards").asInt(0), MAX_SHARDS));
        for (int i = 0; i < shards; i++) {
            var shard = projections.find("tenders.list", "s" + i);
            if (shard.isEmpty()) {
                continue;
            }
            for (JsonNode t : MAPPER.readTree(shard.get().data()).path("tenders")) {
                if (tenderId.equals(t.path("id").asString(null))) {
                    String deadline = t.path("deadline").asString(null);
                    try {
                        return new Tender(deadline == null || deadline.isBlank() ? null : Instant.parse(deadline));
                    } catch (java.time.format.DateTimeParseException e) {
                        return new Tender(null);
                    }
                }
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Gara non trovata.");
    }
}
