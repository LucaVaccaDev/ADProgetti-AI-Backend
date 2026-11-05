package it.adprogetti.zio.edge.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.contracts.payload.JobsCancelPayload;
import it.adprogetti.zio.contracts.payload.JobsCancelResult;
import it.adprogetti.zio.contracts.payload.JobsRunResult;
import it.adprogetti.zio.contracts.payload.JobsRunPayload;
import it.adprogetti.zio.contracts.payload.JobsSummaryProjection;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.channel.ConnectionState;
import it.adprogetti.zio.edge.channel.HubConnection;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** jobs.run / jobs.cancel from the (fake) Hub, and the jobs.summary projection. */
class JobsCommandsIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired HubConnection connection;
    @Autowired JobService jobs;

    @BeforeEach
    void ready() throws Exception {
        for (int i = 0; i < 100 && !(hub.isConnected() && connection.state() == ConnectionState.CONNECTED); i++) {
            Thread.sleep(50);
        }
        hub.clear();
    }

    private CommandResult send(String type, Object payload, String permission) throws Exception {
        HubCommand c = new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), type, "user-1",
                List.of(permission), System.currentTimeMillis(), Instant.now().plusSeconds(60).toEpochMilli(),
                UUID.randomUUID().toString().replace("-", ""), Payloads.write(payload));
        hub.send(hub.sign(c));
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.RESULT,
                f -> ChannelCodec.decodeBody(f, CommandResult.class).commandId().equals(c.id()), 15000);
        assertThat(frame).as("RESULT").isNotNull();
        return ChannelCodec.decodeBody(frame, CommandResult.class);
    }

    @Test
    void anAllowedJobIsStartedByHandAndRuns() throws Exception {
        CommandResult r = send("jobs.run", new JobsRunPayload("SYSTEM_SELFCHECK"), "SETTINGS_MANAGE");
        assertThat(r.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        JobsRunResult result = Payloads.read(r.result(), JobsRunResult.class);
        assertThat(result.created()).isTrue();
        UUID id = UUID.fromString(result.jobId());
        JobEngineTest.waitFor(() -> jobs.find(id).map(j -> j.status() == JobStatus.SUCCEEDED).orElse(false), 10);
        JobView job = jobs.find(id).orElseThrow();
        assertThat(job.requestedBy()).isEqualTo("user-1");
        assertThat(job.priority()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'JOB_RUN' and outcome = 'SUCCESS'",
                Integer.class)).isPositive();
    }

    @Test
    void aTypeOutsideTheEdgeWhitelistIsRefusedEvenIfSigned() throws Exception {
        for (String type : List.of("TEST_OK", "PROTOCOL_RUN", "NO_SUCH_JOB")) {
            CommandResult r = send("jobs.run", new JobsRunPayload(type), "SETTINGS_MANAGE");
            assertThat(r.status()).as(type).isEqualTo(CommandResult.Status.FAILED);
            assertThat(r.errorCode()).isEqualTo("NOT_ALLOWED");
        }
        assertThat(jdbc.queryForObject("select count(*) from job where requested_by = 'user-1' and type in "
                + "('TEST_OK','PROTOCOL_RUN','NO_SUCH_JOB')", Integer.class)).isZero();
    }

    @Test
    void aQueuedJobCanBeCancelledAndAnUnknownOneIsReported() throws Exception {
        UUID id = jobs.enqueue(new JobService.Request("TEST_OK", null, null, "cmd:" + UUID.randomUUID(), null, 5,
                Duration.ofMinutes(10), "tester")).id();
        CommandResult ok = send("jobs.cancel", new JobsCancelPayload(id.toString()), "SETTINGS_MANAGE");
        assertThat(ok.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        assertThat(Payloads.read(ok.result(), JobsCancelResult.class).outcome()).isEqualTo("CANCELLED");

        CommandResult missing = send("jobs.cancel", new JobsCancelPayload(UUID.randomUUID().toString()),
                "SETTINGS_MANAGE");
        assertThat(missing.status()).isEqualTo(CommandResult.Status.FAILED);
        assertThat(missing.errorCode()).isEqualTo("NOT_FOUND");
        CommandResult again = send("jobs.cancel", new JobsCancelPayload(id.toString()), "SETTINGS_MANAGE");
        assertThat(again.errorCode()).isEqualTo("ALREADY_FINISHED");
    }

    @Test
    void theSummaryProjectionCarriesMetadataOnly() throws Exception {
        UUID id = jobs.enqueue(new JobService.Request("TEST_OK", "{\"secret\":\"do-not-leak\"}", "client-folder-xyz",
                "sum:" + UUID.randomUUID(), null, 5, null, "tester")).id();
        JobEngineTest.waitFor(() -> jobs.find(id).map(j -> j.status() == JobStatus.SUCCEEDED).orElse(false), 10);
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.PROJECTION, f -> {
            ProjectionUpdate u = ChannelCodec.decodeBody(f, ProjectionUpdate.class);
            return u.projection().equals("jobs.summary") && u.data().contains(id.toString());
        }, 15000);
        assertThat(frame).as("jobs.summary projection").isNotNull();
        ProjectionUpdate update = ChannelCodec.decodeBody(frame, ProjectionUpdate.class);
        assertThat(update.entityId()).isEqualTo("all");
        assertThat(update.data()).doesNotContain("do-not-leak").doesNotContain("client-folder-xyz");
        JobsSummaryProjection summary = Payloads.read(update.data(), JobsSummaryProjection.class);
        assertThat(summary.recent()).anySatisfy(j -> {
            assertThat(j.id()).isEqualTo(id.toString());
            assertThat(j.status()).isIn("QUEUED", "RUNNING", "SUCCEEDED");
        });
        assertThat(summary.schedules()).extracting(JobsSummaryProjection.Schedule::name).contains("protocol-run");
        assertThat(summary.schedules()).filteredOn(s -> s.name().equals("protocol-run")).singleElement()
                .satisfies(s -> {
                    assertThat(s.enabled()).isFalse();
                    assertThat(s.times()).containsExactly("14:30", "18:30");
                });
    }
}
