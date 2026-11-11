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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends {@code tenders.list} to the Hub: the tenders still open, soonest deadline first, in shards that each fit the
 * Hub's size limit, then an index (see {@link TendersListProjection}). Public data only. Sent at every reconnection,
 * after each sync, and when something changed; unchanged shards are not sent again.
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
    /** Tenders per shard, at most (each shard is also cut by size). */
    static final int SHARD_SIZE = 120;

    private final Map<String, String> lastSent = new ConcurrentHashMap<>();

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
        store.purgeExpired();
        publish(false);
    }

    void publishSoon() {
        publish(false);
    }

    record Built(List<TendersListProjection> shards, TendersListProjection index) {
    }

    Built build() {
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
        List<List<TendersListProjection.Tender>> chunks = chunks(items);
        List<TendersListProjection> shards = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            shards.add(new TendersListProjection(i, chunks.size(), total, List.of(), chunks.get(i)));
        }
        return new Built(shards, new TendersListProjection(TendersListProjection.INDEX, chunks.size(), total, sources, List.of()));
    }

    /** Cuts the list into runs of at most {@link #SHARD_SIZE} tenders whose JSON stays under the size limit. */
    static List<List<TendersListProjection.Tender>> chunks(List<TendersListProjection.Tender> items) {
        List<List<TendersListProjection.Tender>> out = new ArrayList<>();
        List<TendersListProjection.Tender> current = new ArrayList<>();
        int size = 0;
        for (var t : items) {
            int own = Payloads.write(new TendersListProjection(0, 1, 0, List.of(), List.of(t))).length();
            if (!current.isEmpty() && (current.size() >= SHARD_SIZE || size + own > MAX_JSON_CHARS - 2_000)) {
                out.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(t);
            size += own;
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
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
            Built built = build();
            long version = clock.millis();
            // Shards first, the index last: the Hub switches to the new list only when the index arrives.
            for (TendersListProjection shard : built.shards()) {
                send("s" + shard.shard(), shard, version, force);
            }
            send("all", built.index(), version, force);
        } catch (RuntimeException e) {
            log.warn("Tenders projection not published: {}", e.getClass().getSimpleName());
        }
    }

    private void send(String entity, TendersListProjection message, long version, boolean force) {
        String json = Payloads.write(message);
        if (!force && json.equals(lastSent.get(entity))) {
            return;
        }
        outbox.enqueue(ChannelEnvelope.Kind.PROJECTION, new ProjectionUpdate(ContractVersion.CURRENT,
                "tenders.list:" + entity + ":" + version, "tenders.list", entity, version, json));
        lastSent.put(entity, json);
    }
}
