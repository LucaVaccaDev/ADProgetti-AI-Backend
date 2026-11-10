package it.adprogetti.zio.edge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalAiFilterTest {

    private static String reason(String text, String... terms) {
        return ExternalAiFilter.check(text, List.of(terms), 4000).reason();
    }

    @Test
    void generalTechnicalQuestionsPass() {
        assertThat(ExternalAiFilter.check("Qual è la differenza tra un solaio in latero-cemento e uno in acciaio?",
                List.of(), 4000).allowed()).isTrue();
        assertThat(ExternalAiFilter.check("Cosa prevede la norma UNI EN 1992-1-1 per il copriferro?", List.of(), 4000)
                .allowed()).isTrue();
    }

    @Test
    void emailTaxCodeVatAndNasPathsAreBlocked() {
        assertThat(reason("scrivi a mario.rossi@studio.it per la risposta")).isEqualTo("EMAIL");
        assertThat(reason("il cliente RSSMRA80A01H501U ha chiesto")).isEqualTo("TAX_CODE");
        assertThat(reason("omocodia rssmra80a01h50lu")).isEqualTo("TAX_CODE");
        assertThat(reason("P.IVA 01234567890 del fornitore")).isEqualTo("VAT_NUMBER");
        assertThat(reason("partita IVA IT01234567890")).isEqualTo("VAT_NUMBER");
        for (String path : List.of("apri archivio:/Protocolli/2025/x.pdf", "smb://192.168.1.57/Gare",
                "\\\\192.168.1.57\\Archivio\\x", "in /Volumes/Gare/GARE 2025", "C:\\Users\\x\\file.docx",
                "file:///Users/x/a.pdf")) {
            assertThat(reason(path)).as(path).isEqualTo("NAS_PATH");
        }
    }

    @Test
    void listedJobAndClientNamesAreBlockedWhateverTheAccentsCaseOrPunctuation() {
        List<String> terms = List.of("Scuola Baronio", "Comune di Sora");
        assertThat(reason("calcola il carico per la scuola BARONIO", "Scuola Baronio")).isEqualTo("SENSITIVE_NAME");
        assertThat(reason("progetto del comune-di sora, tetto", "Comune di Sora")).isEqualTo("SENSITIVE_NAME");
        assertThat(ExternalAiFilter.check("che cos'è un comune?", terms, 4000).allowed()).isTrue();
        assertThat(ExternalAiFilter.check("sorabaronio è una parola unica", terms, 4000).allowed()).isTrue();
        assertThat(ExternalAiFilter.check("ab ok", List.of("ab"), 4000).allowed()).isTrue(); // too short to be a name
        assertThat(ExternalAiFilter.check("Società Èdile ROSSI", List.of("società edile rossi"), 4000).reason())
                .isEqualTo("SENSITIVE_NAME");
    }

    @Test
    void emptyAndTooLongTextsAreBlocked() {
        assertThat(reason("   ")).isEqualTo("EMPTY");
        assertThat(reason(null)).isEqualTo("EMPTY");
        assertThat(ExternalAiFilter.check("x".repeat(4001), List.of(), 4000).reason()).isEqualTo("TOO_LONG");
    }

    @Test
    void theVerdictNeverQuotesTheMatchedText() {
        assertThat(ExternalAiFilter.check("mario.rossi@studio.it", List.of(), 4000).toString())
                .doesNotContain("mario").doesNotContain("studio");
    }
}
