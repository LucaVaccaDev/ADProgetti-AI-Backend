package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import it.adprogetti.zio.contracts.message.EdgeHello;
import it.adprogetti.zio.contracts.message.ServiceStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** What the Hub knows of the Edge: connected only if the socket is open and the heartbeat is recent. */
@Service
public class EdgeStateService {

    public record EdgeStatus(boolean connected, Instant lastSeenAt, String edgeVersion, List<ServiceStatus> services) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock clock;
    private final EdgeSessions sessions;
    private final ChannelProperties props;

    EdgeStateService(JdbcClient jdbc, Clock clock, EdgeSessions sessions, ChannelProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.sessions = sessions;
        this.props = props;
    }

    void onHello(EdgeHello hello) {
        upsert(hello.edgeVersion(), null);
    }

    void onHeartbeat(EdgeHeartbeat beat) {
        upsert(beat.edgeVersion(), MAPPER.writeValueAsString(beat.services()));
    }

    private void upsert(String version, String services) {
        String v = version == null ? null : version.substring(0, Math.min(40, version.length()));
        jdbc.sql("""
                insert into edge_state (id, last_seen_at, edge_version, services) values (1, :t, :v, :s)
                on conflict (id) do update set last_seen_at = :t, edge_version = :v,
                   services = coalesce(:s, edge_state.services)
                """).param("t", Timestamp.from(clock.instant())).param("v", v).param("s", services).update();
    }

    public EdgeStatus status() {
        return jdbc.sql("select last_seen_at, edge_version, services from edge_state where id = 1")
                .query((rs, n) -> {
                    Instant seen = rs.getTimestamp("last_seen_at").toInstant();
                    boolean connected = sessions.isConnected()
                            && seen.isAfter(clock.instant().minus(props.heartbeatTimeout()));
                    return new EdgeStatus(connected, seen, rs.getString("edge_version"),
                            connected ? parse(rs.getString("services")) : List.of());
                }).optional().orElse(new EdgeStatus(false, null, null, List.of()));
    }

    private static List<ServiceStatus> parse(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<ServiceStatus>>() {
            });
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
