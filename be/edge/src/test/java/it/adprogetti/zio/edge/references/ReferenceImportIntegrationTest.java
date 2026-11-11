package it.adprogetti.zio.edge.references;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Import of the references against a real Postgres: idempotence, approvals kept, changes sent back to verification. */
class ReferenceImportIntegrationTest extends EdgeIntegrationTestBase {

    @Autowired ReferenceImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from reference_service");
    }

    private ReferenceImportService.Result importSample(TestReferencesXlsx x) {
        var sheet = XlsxReader.read(new ByteArrayInputStream(x.build()), "10 ANNI");
        return service.importSheet(sheet, "documenti:/docs-index/referenze.xlsx", "admin");
    }

    @Test
    void firstImportCreatesServicesAndAmountsAndAStatusPerService() {
        var r = importSample(TestReferencesXlsx.sample());
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.services()).isEqualTo(3);
        assertThat(r.created()).isEqualTo(3);
        assertThat(r.toVerify()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from reference_service where cig = '83974903F8'", String.class))
                .isEqualTo("DA_APPROVARE");
        assertThat(jdbc.queryForObject("select count(*) from reference_service where status = 'DA_VERIFICARE'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select sum(a.amount) from reference_work_amount a join reference_service s on s.id = a.service_id
                where s.cig = '83974903F8'""", java.math.BigDecimal.class)).isEqualByComparingTo("1500000.50");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'REFERENCES_IMPORT'", Integer.class))
                .isPositive();
    }

    @Test
    void aSecondImportOfTheSameFileChangesNothing() {
        importSample(TestReferencesXlsx.sample());
        var again = importSample(TestReferencesXlsx.sample());
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.unchanged()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from reference_service", Integer.class)).isEqualTo(3);
    }

    @Test
    void anApprovedReferenceStaysApprovedUntilTheSheetChangesIt() {
        importSample(TestReferencesXlsx.sample());
        jdbc.update("update reference_service set status = 'APPROVATA' where cig = '83974903F8'");
        importSample(TestReferencesXlsx.sample());
        assertThat(jdbc.queryForObject("select status from reference_service where cig = '83974903F8'", String.class))
                .isEqualTo("APPROVATA");

        // the same service, now with another amount for E.04
        var changed = new TestReferencesXlsx("10 ANNI")
                .row(1, "A=Località esecuzione servizio", "D=Comune Alfa (XX)", "G=Località esecuzione servizio")
                .row(2, "A=Periodo esecuzione servizio", "D=09/2020-12/2020")
                .row(3, "A=Oggetto del servizio", "D=Progetto esecutivo scuola")
                .row(4, "A=Committente", "D=Comune Alfa")
                .row(5, "A=Ruolo e percentuale di partecipazione", "D=Capogruppo 90%")
                .row(6, "A=CIG", "D=83974903F8", "G=CIG")
                .row(8, "A=I/d", "B=E.08", "C=#0.95", "D=#1000000.5", "G=#1000000.5")
                .row(9, "D=#600000", "G=#600000", "H=E.04", "I=#1.2")
                .row(11, "A=TOTALE");
        var r = importSample(changed);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.changedAfterApproval()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from reference_service where cig = '83974903F8'", String.class))
                .isEqualTo("DA_VERIFICARE");
        assertThat(jdbc.queryForObject("select issues from reference_service where cig = '83974903F8'", String.class))
                .contains("MODIFICATA_DOPO_APPROVAZIONE");
        // the services not in this file are left alone
        assertThat(jdbc.queryForObject("select count(*) from reference_service", Integer.class)).isEqualTo(3);
    }

    @Test
    void withoutAConfiguredFileTheRunIsRefusedWithACode() {
        assertThat(service.run("admin").errorCode()).isEqualTo("NOT_CONFIGURED");
    }
}
