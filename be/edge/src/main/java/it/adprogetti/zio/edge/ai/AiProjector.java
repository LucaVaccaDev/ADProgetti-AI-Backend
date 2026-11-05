package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.payload.AiModelsResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.channel.HubConnectedEvent;
import it.adprogetti.zio.edge.channel.OutboxService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends the read-only AI projections to the Hub: installed models with assignments ({@code ai.models}) and
 * usage metadata ({@code ai.usage}). Nothing that could contain studio content (CLAUDE.md section 4).
 */
@Service
public class AiProjector {

    private static final Logger log = LoggerFactory.getLogger(AiProjector.class);
    private static final Duration USAGE_WINDOW = Duration.ofDays(30);

    private final ModelCatalog catalog;
    private final AiUsageLog usage;
    private final OutboxService outbox;
    private final Clock clock;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edge-ai-projector");
        t.setDaemon(true);
        return t;
    });

    AiProjector(ModelCatalog catalog, AiUsageLog usage, OutboxService outbox, Clock clock) {
        this.catalog = catalog;
        this.usage = usage;
        this.outbox = outbox;
        this.clock = clock;
    }

    public void publishModels(AiModelsResult models) {
        publish("ai.models", Payloads.write(models));
    }

    public void publishUsage() {
        publish("ai.usage", Payloads.write(usage.summary(USAGE_WINDOW)));
    }

    @EventListener(HubConnectedEvent.class)
    void onConnected() {
        background.execute(this::refresh);
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    void periodic() {
        if (outbox.isConnected()) {
            background.execute(this::refresh);
        }
    }

    private void refresh() {
        try {
            publishUsage();
            publishModels(catalog.snapshot());
        } catch (AiUnavailableException e) {
            log.warn("AI projections: local AI not reachable ({})", e.code());
        } catch (RuntimeException e) {
            log.warn("AI projections not published: {}", e.getClass().getSimpleName());
        }
    }

    private void publish(String name, String data) {
        long version = clock.millis();
        outbox.enqueue(ChannelEnvelope.Kind.PROJECTION,
                new ProjectionUpdate(ContractVersion.CURRENT, name + ":" + version, name, "all", version, data));
    }

    @PreDestroy
    void stop() {
        background.shutdownNow();
    }
}
