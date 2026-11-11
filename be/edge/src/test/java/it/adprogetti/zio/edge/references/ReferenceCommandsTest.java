package it.adprogetti.zio.edge.references;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.contracts.payload.ReferencesDecidePayload;
import it.adprogetti.zio.contracts.payload.ReferencesListPayload;
import it.adprogetti.zio.contracts.payload.ReferencesResult;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** references.list and references.decide through the real channel: permission, rules, audit. */
class ReferenceCommandsTest extends EdgeIntegrationTestBase {

    @Autowired ReferenceImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from reference_service");
        service.importSheet(XlsxReader.read(new ByteArrayInputStream(TestReferencesXlsx.sample().build()), "10 ANNI"),
                "documenti:/docs-index/referenze.xlsx", "admin");
    }

    private CommandResult send(String type, Object payload, String permission) throws Exception {
        HubCommand c = new HubCommand(ContractVersion.CURRENT, UUID.randomUUID().toString(), type, "mario",
                List.of(permission), System.currentTimeMillis(), Instant.now().plusSeconds(60).toEpochMilli(),
                UUID.randomUUID().toString().replace("-", ""), Payloads.write(payload));
        hub.send(hub.sign(c));
        ChannelEnvelope frame = hub.await(ChannelEnvelope.Kind.RESULT,
                f -> ChannelCodec.decodeBody(f, CommandResult.class).commandId().equals(c.id()), 15000);
        assertThat(frame).as("RESULT").isNotNull();
        return ChannelCodec.decodeBody(frame, CommandResult.class);
    }

    private ReferencesResult list(String status) throws Exception {
        CommandResult r = send("references.list", new ReferencesListPayload(status, 0, 50), "TENDERS_ALL");
        assertThat(r.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        return Payloads.read(r.result(), ReferencesResult.class);
    }

    private long idOfCig(String cig) {
        return jdbc.queryForObject("select id from reference_service where cig = ?", Long.class, cig);
    }

    @Test
    void listsWithCountsAndAmountsAndFiltersByStatus() throws Exception {
        ReferencesResult all = list(null);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.counts().daApprovare()).isEqualTo(1);
        assertThat(all.counts().daVerificare()).isEqualTo(2);
        var alfa = all.items().stream().filter(i -> "83974903F8".equals(i.cig())).findFirst().orElseThrow();
        assertThat(alfa.totalAmount()).isEqualTo(1500000.5);
        assertThat(alfa.amounts()).extracting(ReferencesResult.Amount::workId).containsExactly("E.04", "E.08");
        assertThat(list("DA_VERIFICARE").items()).hasSize(2).allSatisfy(i -> assertThat(i.issues()).isNotEmpty());
    }

    @Test
    void needsTheTenderManagerPermission() throws Exception {
        assertThat(send("references.list", new ReferencesListPayload(null, 0, 10), "TENDERS_WORK").status())
                .isEqualTo(CommandResult.Status.REJECTED);
        assertThat(send("references.decide", new ReferencesDecidePayload(1, "APPROVATA"), "SETTINGS_MANAGE").status())
                .isEqualTo(CommandResult.Status.REJECTED);
    }

    @Test
    void approveDiscardAndReopenWithAnAuditTrail() throws Exception {
        long id = idOfCig("83974903F8");
        CommandResult approved = send("references.decide", new ReferencesDecidePayload(id, "APPROVATA"), "TENDERS_ALL");
        assertThat(approved.status()).isEqualTo(CommandResult.Status.SUCCEEDED);
        assertThat(Payloads.read(approved.result(), ReferencesResult.class).items().get(0).status()).isEqualTo("APPROVATA");
        assertThat(list(null).counts().approvata()).isEqualTo(1);

        send("references.decide", new ReferencesDecidePayload(id, "SCARTATA"), "TENDERS_ALL");
        assertThat(jdbc.queryForObject("select status from reference_service where id = ?", String.class, id))
                .isEqualTo("SCARTATA");
        // from SCARTATA you reopen first, you do not jump to APPROVATA
        assertThat(send("references.decide", new ReferencesDecidePayload(id, "APPROVATA"), "TENDERS_ALL").errorCode())
                .isEqualTo("NOT_APPROVABLE");
        send("references.decide", new ReferencesDecidePayload(id, "DA_APPROVARE"), "TENDERS_ALL");
        assertThat(jdbc.queryForObject("select status from reference_service where id = ?", String.class, id))
                .isEqualTo("DA_APPROVARE");
        String audit = jdbc.queryForObject("select string_agg(detail, ' ; ') from audit_log "
                + "where action = 'REFERENCE_DECISION'", String.class);
        assertThat(audit).contains("by mario: DA_APPROVARE -> APPROVATA").contains("APPROVATA -> SCARTATA");
    }

    @Test
    void aReferenceWithUnreadableDataCannotBeApprovedNorReopened() throws Exception {
        long id = idOfCig("ZA61F7E6EF");
        assertThat(send("references.decide", new ReferencesDecidePayload(id, "APPROVATA"), "TENDERS_ALL").errorCode())
                .isEqualTo("NOT_APPROVABLE");
        assertThat(send("references.decide", new ReferencesDecidePayload(id, "DA_APPROVARE"), "TENDERS_ALL").errorCode())
                .isEqualTo("NOT_APPROVABLE");
        assertThat(send("references.decide", new ReferencesDecidePayload(id, "SCARTATA"), "TENDERS_ALL").status())
                .isEqualTo(CommandResult.Status.SUCCEEDED);
        assertThat(send("references.decide", new ReferencesDecidePayload(999999, "SCARTATA"), "TENDERS_ALL").errorCode())
                .isEqualTo("NOT_FOUND");
    }
}
