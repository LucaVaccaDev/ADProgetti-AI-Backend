package it.adprogetti.zio.edge.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private static String sentences(int n, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(prefix).append(" frase numero ").append(i).append(" del documento di prova. ");
        }
        return sb.toString().strip();
    }

    @Test
    void shortTextIsOneChunkOnItsPage() {
        List<Chunker.Chunk> chunks = Chunker.split(List.of(new PdfTextExtractor.Page(4, sentences(3, "a"))), 800, 100);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageFrom()).isEqualTo(4);
        assertThat(chunks.get(0).pageTo()).isEqualTo(4);
        assertThat(chunks.get(0).ordinal()).isZero();
    }

    @Test
    void longTextIsSplitAtBoundariesWithOverlapAndNothingIsLost() {
        String text = sentences(60, "b");
        List<Chunker.Chunk> chunks = Chunker.split(List.of(new PdfTextExtractor.Page(1, text)), 400, 80);
        assertThat(chunks.size()).isGreaterThan(5);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).ordinal()).isEqualTo(i);
            assertThat(chunks.get(i).content().length()).isLessThanOrEqualTo(400);
            assertThat(chunks.get(i).content()).doesNotStartWith(" ");
        }
        // overlap: the beginning of a chunk reappears at the end of the previous one
        String head = chunks.get(1).content().substring(0, 20);
        assertThat(chunks.get(0).content()).contains(head);
        // every sentence is complete in at least one chunk (breaks land on sentence ends first)
        for (int i = 0; i < 60; i++) {
            String sentence = "b frase numero " + i + " del documento di prova.";
            assertThat(chunks.stream().anyMatch(c -> c.content().contains(sentence))).as(sentence).isTrue();
        }
    }

    @Test
    void passagesRememberTheirPages() {
        List<PdfTextExtractor.Page> pages = List.of(new PdfTextExtractor.Page(1, sentences(20, "uno")),
                new PdfTextExtractor.Page(2, sentences(20, "due")), new PdfTextExtractor.Page(7, sentences(20, "sette")));
        List<Chunker.Chunk> chunks = Chunker.split(pages, 500, 60);
        assertThat(chunks.get(0).pageFrom()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).pageTo()).isEqualTo(7);
        assertThat(chunks).allSatisfy(c -> assertThat(c.pageTo()).isGreaterThanOrEqualTo(c.pageFrom()));
        assertThat(chunks).anySatisfy(c -> assertThat(c.content()).contains("sette").hasSize(c.content().length()));
        assertThat(chunks.stream().filter(c -> c.content().contains("sette")).allMatch(c -> c.pageTo() == 7)).isTrue();
    }

    @Test
    void aWordWithoutSpacesIsStillCutAndTinyFragmentsAreDropped() {
        List<Chunker.Chunk> chunks = Chunker.split(List.of(new PdfTextExtractor.Page(1, "x".repeat(2000))), 500, 50);
        assertThat(chunks.size()).isGreaterThan(3);
        assertThat(Chunker.split(List.of(new PdfTextExtractor.Page(1, "ok")), 500, 50)).isEmpty();
        assertThat(Chunker.split(List.of(), 500, 50)).isEmpty();
    }
}
