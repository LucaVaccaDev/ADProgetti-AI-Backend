package it.adprogetti.zio.edge.jobs;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Persistent scheduler (capitolato 7.10, B.18). It does not fire timers: every 30 seconds it computes the latest
 * slot that is due and, if that slot was not handed to the queue yet, enqueues one job for it. So a slot missed
 * while the Edge was off is recovered at the next tick (only the latest one: each run processes the whole
 * backlog anyway), and a restart never relaunches a slot already queued (state in the database plus the
 * idempotency key). The schedule name is the resource key, so two runs of the same schedule never overlap.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final int LOOKBACK_DAYS = 2;

    private final JobsProperties props;
    private final JobRepository repository;
    private final JobService jobs;
    private final Clock clock;

    ScheduleService(JobsProperties props, JobRepository repository, JobService jobs, Clock clock) {
        this.props = props;
        this.repository = repository;
        this.jobs = jobs;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        tick();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    void tick() {
        tick(clock.instant());
    }

    /** Public for tests with a controlled clock. */
    public synchronized void tick(Instant now) {
        props.schedules().forEach((name, schedule) -> {
            if (!schedule.enabled()) {
                return;
            }
            try {
                tickOne(name, schedule, now);
            } catch (RuntimeException e) {
                log.warn("Schedule {} not advanced: {}", name, e.getClass().getSimpleName());
            }
        });
    }

    private void tickOne(String name, JobsProperties.Schedule schedule, Instant now) {
        Optional<Instant> due = latestSlot(schedule, now);
        if (due.isEmpty()) {
            return;
        }
        Optional<Instant> last = repository.lastSlot(name);
        if (last.isEmpty()) {
            // First activation: no catch-up for slots before the schedule existed.
            repository.saveSlot(name, due.get());
            return;
        }
        if (!due.get().isAfter(last.get())) {
            return;
        }
        jobs.enqueue(new JobService.Request(schedule.type(), null, "slot:" + due.get(), name + ":" + due.get(),
                name, JobService.DEFAULT_PRIORITY, null, "scheduler"));
        repository.saveSlot(name, due.get());
        log.info("Schedule {} queued slot {}", name, due.get());
    }

    /** The most recent slot at or before {@code now}, in the schedule's own time zone (handles daylight saving). */
    static Optional<Instant> latestSlot(JobsProperties.Schedule schedule, Instant now) {
        ZoneId zone = ZoneId.of(schedule.zone());
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant best = null;
        for (int back = 0; back <= LOOKBACK_DAYS; back++) {
            LocalDate day = today.minusDays(back);
            for (LocalTime time : schedule.times()) {
                Instant slot = ZonedDateTime.of(day, time, zone).toInstant();
                if (!slot.isAfter(now) && (best == null || slot.isAfter(best))) {
                    best = slot;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
