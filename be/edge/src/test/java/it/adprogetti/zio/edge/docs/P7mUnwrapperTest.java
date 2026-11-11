package it.adprogetti.zio.edge.docs;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.support.TestPdfs;
import java.util.Random;
import org.junit.jupiter.api.Test;

class P7mUnwrapperTest {

    private static final byte[] PDF = TestPdfs.text("Pagina uno del disciplinare di prova");

    @Test
    void takesTheDocumentOutOfAnEnvelopeWithDefiniteLengths() {
        assertThat(P7mUnwrapper.unwrap(TestP7m.der(PDF))).hasValue(PDF);
    }

    @Test
    void takesTheDocumentOutOfAnEnvelopeWithIndefiniteLengthsAndChunks() {
        assertThat(P7mUnwrapper.unwrap(TestP7m.ber(PDF))).hasValue(PDF);
    }

    @Test
    void unwrapsAnEnvelopeInsideAnotherEnvelope() {
        assertThat(P7mUnwrapper.unwrap(TestP7m.der(TestP7m.ber(PDF)))).hasValue(PDF);
    }

    @Test
    void anythingElseGivesNothingAndNeverThrows() {
        assertThat(P7mUnwrapper.unwrap(PDF)).isEmpty(); // a plain PDF is not an envelope
        assertThat(P7mUnwrapper.unwrap("niente di tutto questo".getBytes())).isEmpty();
        assertThat(P7mUnwrapper.unwrap(new byte[0])).isEmpty();
        byte[] truncated = java.util.Arrays.copyOf(TestP7m.der(PDF), 60);
        assertThat(P7mUnwrapper.unwrap(truncated)).isEmpty();
        Random random = new Random(42); // hostile bytes after a valid start
        for (int i = 0; i < 200; i++) {
            byte[] junk = new byte[200];
            random.nextBytes(junk);
            junk[0] = 0x30;
            junk[1] = (byte) 0x80;
            P7mUnwrapper.unwrap(junk); // must not throw or hang
        }
    }
}
