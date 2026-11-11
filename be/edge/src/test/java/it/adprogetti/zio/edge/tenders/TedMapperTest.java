package it.adprogetti.zio.edge.tenders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TedMapper on notices recorded from TED's public API (src/test/resources/ted/search-page.json). */
class TedMapperTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static List<JsonNode> notices() throws Exception {
        JsonNode root = MAPPER.readTree(Files.readString(Path.of("src/test/resources/ted/search-page.json")));
        List<JsonNode> out = new ArrayList<>();
        root.get("notices").forEach(out::add);
        return out;
    }

    @Test
    void aRealNoticeBecomesARow() throws Exception {
        TenderRow row = TedMapper.map(notices().get(0)).orElseThrow();
        assertThat(row.number()).isEqualTo("756813-2026");
        assertThat(row.title()).startsWith("Servizio di gestione del ciclotrone");
        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("4919384.00"));
        assertThat(row.currency()).isEqualTo("EUR");
        assertThat(row.deadline()).isNotNull();
        assertThat(row.published().toString()).isEqualTo("2026-11-02");
        assertThat(row.procedure()).isEqualTo("open");
        assertThat(row.cpv()).containsExactly("33693000", "71900000", "50400000");
        assertThat(row.place()).isEqualTo("MONZA (MB)");
        assertThat(row.lots()).isEqualTo(1);
    }

    @Test
    void countryAndCpvPrefixAreStrippedFromTheTitle() throws Exception {
        TenderRow row = TedMapper.map(notices().get(2)).orElseThrow();
        assertThat(row.title()).startsWith("PROCEDURA APERTA PER AFFIDAMENTO").doesNotContain("Italia –");
    }

    @Test
    void aNoticeWithoutNumberOrTitleIsSkippedNotGuessed() throws Exception {
        var broken = (tools.jackson.databind.node.ObjectNode) notices().get(0).deepCopy();
        broken.remove("notice-title");
        assertThat(TedMapper.map(broken)).isEmpty();
        var noNumber = (tools.jackson.databind.node.ObjectNode) notices().get(0).deepCopy();
        noNumber.put("publication-number", "../etc/passwd");
        assertThat(TedMapper.map(noNumber)).isEmpty();
    }

    @Test
    void unexpectedShapesDoNotThrow() throws Exception {
        var odd = (tools.jackson.databind.node.ObjectNode) notices().get(0).deepCopy();
        odd.putObject("estimated-value-proc").put("x", 1);
        odd.put("deadline-receipt-tender-date-lot", "not a date");
        assertThat(TedMapper.map(odd)).isPresent();
    }
}
