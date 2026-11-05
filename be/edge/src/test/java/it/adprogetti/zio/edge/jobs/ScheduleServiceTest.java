package it.adprogetti.zio.edge.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dates are in 2030 so that the real clock (which the running scheduler uses) never reaches them. Rome is UTC+1
 * in winter and UTC+2 in summer, so 14:30 local is 13:30Z or 12:30Z.
 */
class ScheduleServiceTest extends EdgeIntegrationTestBase {

    @Autowired ScheduleService scheduler;
    @Autowired JdbcTemplate jdbc;

    private static final JobsProperties.Schedule PROTOCOL = new JobsProperties.Schedule(true, "TEST_SCHEDULED",
            "Europe/Rome", java.util.List.of(java.time.LocalTime.of(14, 30), java.time.LocalTime.of(18, 30)));

    @BeforeEach
    void clean() {
        jdbc.update("delete from schedule_state where name = 'test-run'");
        jdbc.update("delete from job where resource_key = 'test-run'");
    }

    private int queued() {
        return jdbc.queryForObject("select count(*) from job where resource_key = 'test-run'", Integer.class);
    }

    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void the_first_activation_sets_a_baseline_and_runs_nothing() {
        scheduler.tick(at("2030-01-15T15:00:00Z"));
        assertThat(queued()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from schedule_state where name = 'test-run'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void each_slot_runs_once_at_1430_and_1830_local_time() {
        scheduler.tick(at("2030-01-15T10:00:00Z")); // baseline
        scheduler.tick(at("2030-01-15T13:29:00Z"));
        assertThat(queued()).isZero();
        scheduler.tick(at("2030-01-15T13:30:00Z")); // 14:30 Rome (UTC+1)
        assertThat(queued()).isEqualTo(1);
        scheduler.tick(at("2030-01-15T13:31:00Z"));
        scheduler.tick(at("2030-01-15T14:00:00Z"));
        assertThat(queued()).isEqualTo(1);
        scheduler.tick(at("2030-01-15T17:30:00Z")); // 18:30 Rome
        assertThat(queued()).isEqualTo(2);
    }

    @Test
    void saturdays_and_holidays_run_too() {
        scheduler.tick(at("2030-11-01T20:00:00Z"));
        scheduler.tick(at("2030-11-02T13:30:00Z")); // Saturday 14:30 Rome
        assertThat(queued()).isEqualTo(1);
        scheduler.tick(at("2030-12-24T20:00:00Z")); // evening before Christmas: the missed 18:30 slot is recovered
        assertThat(queued()).isEqualTo(2);
        scheduler.tick(at("2030-12-25T13:30:00Z")); // Christmas 14:30 Rome
        assertThat(queued()).isEqualTo(3);
    }

    @Test
    void a_missed_period_recovers_only_the_latest_slot() {
        scheduler.tick(at("2030-02-01T10:00:00Z"));
        scheduler.tick(at("2030-02-01T17:30:00Z")); // 18:30 of day 1
        assertThat(queued()).isEqualTo(1);
        // the Edge was off for two days; back at 15:00 Rome on day 4
        scheduler.tick(at("2030-02-04T14:00:00Z"));
        assertThat(queued()).isEqualTo(2);
    }

    @Test
    void a_restart_never_relaunches_a_slot_already_queued() {
        scheduler.tick(at("2030-03-01T10:00:00Z"));
        scheduler.tick(at("2030-03-01T13:30:00Z"));
        assertThat(queued()).isEqualTo(1);
        // the saved state is lost: the idempotency key still prevents a second job
        jdbc.update("update schedule_state set last_slot = ? where name = 'test-run'",
                java.sql.Timestamp.from(at("2030-03-01T00:00:00Z")));
        scheduler.tick(at("2030-03-01T13:45:00Z"));
        assertThat(queued()).isEqualTo(1);
    }

    @Test
    void daylight_saving_moves_the_utc_time_but_not_the_local_time() {
        // Last Sunday of March 2030 is the 31st: from then Rome is UTC+2, so 14:30 local is 12:30Z.
        assertThat(ScheduleService.latestSlot(PROTOCOL, at("2030-03-30T13:30:00Z"))).contains(at("2030-03-30T13:30:00Z"));
        assertThat(ScheduleService.latestSlot(PROTOCOL, at("2030-03-31T12:29:00Z"))).contains(at("2030-03-30T17:30:00Z"));
        assertThat(ScheduleService.latestSlot(PROTOCOL, at("2030-03-31T12:30:00Z"))).contains(at("2030-03-31T12:30:00Z"));
        // Last Sunday of October 2030 is the 27th: back to UTC+1, so 18:30 local is 17:30Z.
        assertThat(ScheduleService.latestSlot(PROTOCOL, at("2030-10-27T17:30:00Z"))).contains(at("2030-10-27T17:30:00Z"));
        assertThat(ScheduleService.latestSlot(PROTOCOL, at("2030-10-27T13:29:00Z"))).contains(at("2030-10-26T16:30:00Z"));
    }

    @Test
    void two_runs_of_the_same_schedule_share_a_resource_key_so_they_cannot_overlap() {
        scheduler.tick(at("2030-04-01T05:00:00Z"));
        scheduler.tick(at("2030-04-01T12:30:00Z"));
        scheduler.tick(at("2030-04-01T16:30:00Z"));
        assertThat(jdbc.queryForObject(
                "select count(distinct resource_key) from job where resource_key = 'test-run'", Integer.class))
                .isEqualTo(1);
        assertThat(queued()).isEqualTo(2);
    }
}
