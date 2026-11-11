package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.contracts.payload.TendersListProjection;
import it.adprogetti.zio.edge.channel.HubConnectedEvent;
import it.adprogetti.zio.edge.channel.OutboxService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends {@code tenders.list} to the Hub: the tenders still open, soonest deadline first, trimmed to fit the Hub's size
 * limit. Public data only. Sent at every reconnection, after each sync, and when something changed.
 */
@Service
class TendersProjector {

    private static final Logger log = LoggerFactory.getLogger(TendersProjector.class);
    /** Below the Hub's 65,536-character limit, leaving room for the envelope. */
    static final int MAX_JSON_CHARS = 60_000;

    private final TenderStore store;
    private final TedProperties props;
    private final OutboxService outbox;
    private final Clock clock;
    private final AtomicReference<String> lastSent = new AtomicReference<>();

    TendersProjector(TenderStore store, TedProperties props, OutboxService outbox, Clock clock) {
        this.store = store;
        this.props = props;
        this.outbox = outbox;
        this.clock = clock;
    }

    @EventListener(HubConnectedEvent.class)
    void onConnected() {
        publish(true);
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 60_000)
    void periodic() {
        publish(false);
    }

    void publishSoon() {
        publish(false);
    }

    TendersListProjection build() {
        var state = store.syncState();
        List<TendersListProjection.Tender> items = new ArrayList<>();
        for (TenderRow t : store.open(props.projectionLimit())) {
            items.add(new TendersListProjection.Tender(t.number(), t.title(), t.buyer(),
                    t.amount() == null ? null : t.amount().doubleValue(), t.currency(),
                    t.deadline() == null ? null : t.deadline().toString(), t.published().toString(), t.procedure(), t.cpv(),
                    t.place(), t.lots(), "https://ted.europa.eu/it/notice/-/detail/" + t.number()));
        }
        String syncedAt = state.finishedAt() == null ? null : state.finishedAt().toString();
        TendersListProjection full = new TendersListProjection("TED", syncedAt, state.status(), store.openCount(), items);
        while (items.size() > 1 && Payloads.write(full).length() > MAX_JSON_CHARS) {
            items.remove(items.size() - 1);
            full = new TendersListProjection("TED", syncedAt, state.status(), store.openCount(), items);
        }
        return full;
    }

    private void publish(boolean force) {
        if (!outbox.isConnected()) {
            return;
        }
        try {
            String json = Payloads.write(build());
            if (!force && Objects.equals(lastSent.get(), json)) {
                return;
            }
            long version = clock.millis();
            outbox.enqueue(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(ContractVersion.CURRENT,
                    "tenders.list:" + version, "tenders.list", "all", version, json));
            lastSent.set(json);
        } catch (RuntimeException e) {
            log.warn("Tenders projection not published: {}", e.getClass().getSimpleName());
        }
    }
}
