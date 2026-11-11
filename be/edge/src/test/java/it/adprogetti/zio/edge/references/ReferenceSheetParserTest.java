package it.adprogetti.zio.edge.references;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.edge.references.ReferenceSheetParser.Service;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReferenceSheetParserTest {

    private static ReferenceSheetParser.Parsed parse() {
        var sheet = XlsxReader.read(new ByteArrayInputStream(TestReferencesXlsx.sample().build()), "10 anni");
        return ReferenceSheetParser.parse(sheet);
    }

    @Test
    void readsOneServicePerColumnUpToTheRepeatedLabel() {
        var parsed = parse();
        assertThat(parsed.services()).extracting(Service::column).containsExactly("D", "E", "F");
        Service d = parsed.services().get(0);
        assertThat(d.place()).isEqualTo("Comune Alfa (XX)");
        assertThat(d.cig()).isEqualTo("83974903F8"); // the stray dot is gone
        assertThat(d.periodFrom()).isEqualTo(LocalDate.of(2020, 9, 1));
        assertThat(d.periodTo()).isEqualTo(LocalDate.of(2020, 12, 31));
        assertThat(d.sharePercent()).isEqualByComparingTo("90");
        assertThat(d.approvalRef()).startsWith("Determina n. 12");
        assertThat(d.issues()).isEmpty();
    }

    @Test
    void amountsPerWorkIdIncludingIdsThatOnlyExistInTheRightHandCopy() {
        Service d = parse().services().get(0);
        assertThat(d.amounts()).extracting(ReferenceSheetParser.Amount::workId).containsExactly("E.08", "E.04");
        assertThat(d.amounts().get(0).amount()).isEqualByComparingTo("1000000.50");
        assertThat(d.amounts().get(0).category()).isEqualTo("I/d");
        assertThat(d.amounts().get(0).complexity()).isEqualByComparingTo("0.95");
        assertThat(d.amounts().get(1).complexity()).isEqualByComparingTo("1.20"); // from the copy as well
    }

    @Test
    void whatCannotBeReadIsReportedNotGuessed() {
        var parsed = parse();
        Service e = parsed.services().get(1);
        assertThat(e.cig()).isNull();
        assertThat(e.sharePercent()).isEqualByComparingTo("100"); // the number 1 means 100%
        assertThat(e.periodFrom()).isEqualTo(LocalDate.of(2024, 9, 1));
        assertThat(e.issues()).containsExactly("PERIODO_DA_VERIFICARE"); // an Excel date is not a range
        Service f = parsed.services().get(2);
        assertThat(f.periodFrom()).isNull();
        assertThat(f.issues()).contains("PERIODO_NON_LEGGIBILE", "IMPORTO_NON_NUMERICO");
        assertThat(f.amounts()).isEmpty();
        assertThat(f.issues()).contains("NESSUN_IMPORTO");
    }

    @Test
    void countsWorkIdsWhoseTotalDoesNotAddUp() {
        // E.08: 1000000.5 + 200000 = 1200000.5 (ok). E.04 ok. S.03: 300000 against a total of 999
        assertThat(parse().totalMismatches()).isEqualTo(1);
    }

    @Test
    void aSheetWithAnotherLayoutIsRefused() {
        var other = new TestReferencesXlsx("10 ANNI").row(1, "A=Altro", "B=x").build();
        var sheet = XlsxReader.read(new ByteArrayInputStream(other), "10 ANNI");
        assertThatThrownBy(() -> ReferenceSheetParser.parse(sheet)).hasMessage("LAYOUT_NOT_RECOGNISED");
    }

    @Test
    void unknownSheetAndGarbageFilesGiveCodes() {
        assertThatThrownBy(() -> XlsxReader.read(new ByteArrayInputStream(TestReferencesXlsx.sample().build()), "Nope"))
                .hasMessage("SHEET_NOT_FOUND");
        assertThatThrownBy(() -> XlsxReader.read(new ByteArrayInputStream("not a zip".getBytes()), "10 ANNI"))
                .hasMessageContaining("BAD_FILE");
    }

    @Test
    void shareFromTextOrNumber() {
        assertThat(ReferenceSheetParser.share("Capogruppo 57%", null)).isEqualByComparingTo("57");
        assertThat(ReferenceSheetParser.share("", new XlsxReader.Cell(null, 1.0))).isEqualByComparingTo("100");
        assertThat(ReferenceSheetParser.share("mandataria", null)).isNull();
        assertThat(ReferenceSheetParser.share("250%", null)).isNull();
        assertThat(BigDecimal.ONE).isNotNull();
    }
}
