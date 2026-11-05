package it.adprogetti.zio.edge.channel;

import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Messages to the Hub are saved first and then sent, so a dropped connection or a restart loses nothing.
 * The Hub does not acknowledge results, so after each reconnection the messages sent in the last few
 * minutes are sent again: the Hub ignores repeats (results by command id, projections by key).
 */
@Service
public class OutboxService {

    static final Duration RESEND_WINDOW = Duration.ofMinutes(5);
    static final Duration RETENTION = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AtomicReference<HubLink> link = new AtomicReference<>();

    OutboxService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Saves a message and sends it now if the Hub is connected. */
    public void enqueue(ChannelEnvelope.Kind kind, Object message) {
        jdbc.sql("insert into outbox_message (kind, frame, created_at) values (:k, :f, :t)")
                .param("k", kind.name()).param("f", ChannelCodec.encode(kind, message))
                .param("t", Timestamp.from(clock.instant())).update();
        flush(false);
    }

    void attach(HubLink newLink) {
        link.set(newLink);
    }

    void detach(HubLink oldLink) {
        link.compareAndSet(oldLink, null);
    }

    public boolean isConnected() {
        return link.get() != null;
    }

    /** Sends pending messages; after a reconnection also those sent in the last minutes. */
    public synchronized void flush(boolean afterReconnect) {
        HubLink current = link.get();
        if (current == null) {
            return;
        }
        Instant now = clock.instant();
        List<Row> rows = jdbc.sql("""
                select id, frame from outbox_message
                where sent_at is null or (:again and sent_at > :since) order by id
                """).param("again", afterReconnect).param("since", Timestamp.from(now.minus(RESEND_WINDOW)))
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("frame"))).list();
        for (Row row : rows) {
            if (!current.send(row.frame())) {
                return; // stays unsent; retried at the next flush or reconnection
            }
            jdbc.sql("update outbox_message set sent_at = :t where id = :id")
                    .param("t", Timestamp.from(now)).param("id", row.id()).update();
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void cleanup() {
        jdbc.sql("delete from outbox_message where sent_at is not null and created_at < :cut")
                .param("cut", Timestamp.from(clock.instant().minus(RETENTION))).update();
    }

    private record Row(long id, String frame) {
    }
}
