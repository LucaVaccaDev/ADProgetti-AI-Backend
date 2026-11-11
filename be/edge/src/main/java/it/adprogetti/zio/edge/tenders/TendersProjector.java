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
        List<TendersListProjection.Tender> items = new ArrayList<>();
        for (TenderRow t : store.open(props.projectionLimit())) {
            String url = t.url() != null ? t.url()
                    : TenderStore.SOURCE_TED.equals(t.source()) ? "https://ted.europa.eu/it/notice/-/detail/" + t.number() : null;
            items.add(new TendersListProjection.Tender(t.number(), t.source(), t.title(), t.buyer(),
                    t.amount() == null ? null : t.amount().doubleValue(), t.currency(),
                    t.deadline() == null ? null : t.deadline().toString(), t.published().toString(), t.procedure(), t.cpv(),
                    t.place(), t.lots(), url));
        }
        List<TendersListProjection.SourceState> sources = new ArrayList<>();
        int total = 0;
        for (String source : List.of(TenderStore.SOURCE_TED, TenderStore.SOURCE_EC)) {
            var state = store.syncState(source);
            int open = store.openCount(source);
            total += open;
            sources.add(new TendersListProjection.SourceState(source,
                    state.finishedAt() == null ? null : state.finishedAt().toString(), state.status(), state.errorCode(), open));
        }
        TendersListProjection full = new TendersListProjection(total, sources, items);
        while (items.size() > 1 && Payloads.write(full).length() > MAX_JSON_CHARS) {
            items.remove(items.size() - 1);
            full = new TendersListProjection(total, sources, items);
        }
        return full;
    }

    /** A run left RUNNING by a crash is closed, so the Hub never shows a run that cannot finish. */
    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void closeStale() {
        store.closeStaleSync();
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
