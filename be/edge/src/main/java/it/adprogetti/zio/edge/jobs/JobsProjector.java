package it.adprogetti.zio.edge.jobs;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.payload.JobsSummaryProjection;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.channel.HubConnectedEvent;
import it.adprogetti.zio.edge.channel.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends {@code jobs.summary} to the Hub: counts, the latest jobs and the schedules. Metadata only: no payload, no
 * result, no object reference. Sent when something changed (checked every few seconds), at every reconnection,
 * and right after a manual run or cancellation.
 */
@Service
class JobsProjector {

    private static final Logger log = LoggerFactory.getLogger(JobsProjector.class);
    private static final int RECENT = 30;

    private final JobRepository repository;
    private final JobRegistry registry;
    private final JobsProperties props;
    private final OutboxService outbox;
    private final Clock clock;
    private final AtomicReference<String> lastSent = new AtomicReference<>();

    JobsProjector(JobRepository repository, JobRegistry registry, JobsProperties props, OutboxService outbox,
            Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.props = props;
        this.outbox = outbox;
        this.clock = clock;
    }

    @EventListener(HubConnectedEvent.class)
    void onConnected() {
        publish(true);
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    void periodic() {
        publish(false);
    }

    void publishSoon() {
        publish(false);
    }

    JobsSummaryProjection build() {
        Instant now = clock.instant();
        var counts = repository.counts(now.minus(Duration.ofHours(24)));
        List<JobsSummaryProjection.Job> recent = repository.recent(RECENT).stream().map(j ->
                new JobsSummaryProjection.Job(j.id().toString(), j.type(), j.status().name(), j.attempts(),
                        j.maxAttempts(), str(j.createdAt()), str(j.startedAt()), str(j.finishedAt()), j.errorCode(),
                        j.requestedBy(), !j.status().finished()
                                && (j.status() == JobStatus.QUEUED
                                        || registry.find(j.type()).map(JobHandler::cancellable).orElse(false)))).toList();
        List<JobsSummaryProjection.Schedule> schedules = props.schedules().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new JobsSummaryProjection.Schedule(e.getKey(), e.getValue().type(), e.getValue().enabled(),
                        e.getValue().zone(), e.getValue().times().stream().map(Object::toString).toList(),
                        repository.lastSlot(e.getKey()).map(Instant::toString).orElse(null))).toList();
        return new JobsSummaryProjection(new JobsSummaryProjection.Counts(counts.queued(), counts.running(),
                counts.succeeded(), counts.failed()), recent, schedules);
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
                    "jobs.summary:" + version, "jobs.summary", "all", version, json));
            lastSent.set(json);
        } catch (RuntimeException e) {
            log.warn("Jobs projection not published: {}", e.getClass().getSimpleName());
        }
    }

    private static String str(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
