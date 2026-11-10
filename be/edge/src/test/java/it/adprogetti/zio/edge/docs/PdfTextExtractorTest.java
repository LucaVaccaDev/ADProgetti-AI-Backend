package it.adprogetti.zio.edge.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.adprogetti.zio.edge.support.TestPdfs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private static DocsProperties props(int maxPages, int maxChars) {
        return new DocsProperties(null, 0, 0, maxPages, maxChars, Duration.ofSeconds(30), 800, 100, 16, 0);
    }

    private final PdfTextExtractor extractor = new PdfTextExtractor(props(0, 0));

    private static ByteArrayInputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    @Test
    void extractsItalianTextPageByPageWithAccentsAndTheHashOfTheFile() throws Exception {
        byte[] pdf = TestPdfs.text("Il copriferro minimo è di 30 mm per l'ambiente più aggressivo.",
                "Verifica della portata: così come già calcolato, perché l'armatura resiste.");
        PdfTextExtractor.Extracted e = extractor.extract(in(pdf));
        assertThat(e.totalPages()).isEqualTo(2);
        assertThat(e.pages()).extracting(PdfTextExtractor.Page::number).containsExactly(1, 2);
        assertThat(e.pages().get(0).text()).contains("copriferro").contains("è di 30 mm").contains("più");
        assertThat(e.pages().get(1).text()).contains("così").contains("perché");
        assertThat(e.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
        assertThat(PdfTextExtractor.looksScanned(e)).isFalse();
    }

    @Test
    void aPdfWithoutATextLayerLooksScannedAndIsNeverInvented() throws Exception {
        PdfTextExtractor.Extracted e = extractor.extract(in(TestPdfs.blank(3)));
        assertThat(e.pages()).isEmpty();
        assertThat(e.totalPages()).isEqualTo(3);
        assertThat(PdfTextExtractor.looksScanned(e)).isTrue();
    }

    @Test
    void anAlmostEmptyLongPdfAlsoLooksScanned() throws Exception {
        PdfTextExtractor.Extracted e = extractor.extract(in(TestPdfs.text("1", "2", "3", "4", "5", "6")));
        assertThat(PdfTextExtractor.looksScanned(e)).isTrue();
    }

    @Test
    void aPdfWithOnlyAnOwnerPasswordAndCopyRestrictionsIsStillReadable() throws Exception {
        PdfTextExtractor.Extracted e = extractor.extract(in(TestPdfs.ownerRestricted("Capitolato speciale d'appalto con restrizioni di copia ma leggibile da tutti, come molti file di gara pubblici.")));
        assertThat(e.pages()).hasSize(1);
        assertThat(e.pages().get(0).text()).contains("Capitolato speciale");
    }

    @Test
    void encryptedAndCorruptFilesFailWithShortCodes() {
        assertThatThrownBy(() -> extractor.extract(in(TestPdfs.encrypted("segreto"))))
                .isInstanceOfSatisfying(ExtractionException.class, x -> assertThat(x.code()).isEqualTo("ENCRYPTED"));
        assertThatThrownBy(() -> extractor.extract(in("questo non è un pdf".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(ExtractionException.class, x -> assertThat(x.code()).isEqualTo("PARSE_ERROR"));
        assertThatThrownBy(() -> extractor.extract(in(new byte[0])))
                .isInstanceOfSatisfying(ExtractionException.class, x -> assertThat(x.code()).isEqualTo("PARSE_ERROR"));
        byte[] truncated = TestPdfs.text("testo completo di prova per il troncamento del file");
        assertThatThrownBy(() -> extractor.extract(in(java.util.Arrays.copyOf(truncated, truncated.length / 2))))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void limitsOnPagesAndTextAreEnforced() {
        PdfTextExtractor small = new PdfTextExtractor(props(2, 0));
        assertThatThrownBy(() -> small.extract(in(TestPdfs.text("a", "b", "c"))))
                .isInstanceOfSatisfying(ExtractionException.class, x -> assertThat(x.code()).isEqualTo("TOO_MANY_PAGES"));
        PdfTextExtractor tiny = new PdfTextExtractor(props(0, 50));
        assertThatThrownBy(() -> tiny.extract(in(TestPdfs.text("x ".repeat(200)))))
                .isInstanceOfSatisfying(ExtractionException.class, x -> assertThat(x.code()).isEqualTo("TOO_MUCH_TEXT"));
    }

    @Test
    void cleaningJoinsHyphenatedWordsRemovesNulAndControlCharactersAndCollapsesBlanks() {
        assertThat(PdfTextExtractor.clean("costru-\nzione   di\u0000 un solaio\t\tin\u0007 cemento")).isEqualTo("costruzione di un solaio in cemento");
        assertThat(PdfTextExtractor.clean("riga uno \n\n\n\n riga due")).isEqualTo("riga uno\n\nriga due");
        assertThat(PdfTextExtractor.clean("a-\nB")).contains("-"); // an uppercase after the hyphen is not a split word
    }
}
