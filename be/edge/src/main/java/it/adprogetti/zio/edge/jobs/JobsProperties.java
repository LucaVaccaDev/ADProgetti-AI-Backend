package it.adprogetti.zio.edge.jobs;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Job engine and scheduler settings (edge.jobs.*). */
@ConfigurationProperties("edge.jobs")
public record JobsProperties(int workers, Duration pollInterval, Duration lease, Duration retention,
        Map<String, Schedule> schedules) {

    public JobsProperties {
        workers = workers <= 0 ? 2 : workers;
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        lease = lease == null ? Duration.ofMinutes(2) : lease;
        retention = retention == null ? Duration.ofDays(90) : retention;
        schedules = schedules == null ? Map.of() : Map.copyOf(schedules);
    }

    /**
     * A daily schedule at fixed local times (zone Europe/Rome by default), every day including Saturdays and
     * holidays. The schedule name is also the resource key, so two runs of the same schedule never overlap.
     */
    public record Schedule(boolean enabled, String type, String zone, List<LocalTime> times) {
        public Schedule {
            zone = zone == null || zone.isBlank() ? "Europe/Rome" : zone;
            times = times == null ? List.of() : List.copyOf(times);
        }
    }
}
