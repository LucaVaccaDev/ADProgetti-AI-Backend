package it.adprogetti.zio.edge.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import it.adprogetti.zio.edge.nas.NasMode;
import it.adprogetti.zio.edge.nas.NasProperties;
import it.adprogetti.zio.edge.nas.NasReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Read-only trial of the PDF extraction on real PDFs of the external SSD (GARE 2025), before indexing them for real.
 * Skipped when the SSD is not mounted. No Spring, no database, no embedding. It reads a random sample through the
 * NAS service and prints only counts and times: never a file name or a word of text.
 */
class DocsSsdExtractionTrialTest {

    private static final Path SSD = Path.of("/Volumes/AI");
    private static final String ROOT = "gare:/GARE 2025";
    private static final int SAMPLE = 120;

    @Test
    void realPdfsAreExtractedAndChunkedWithinLimits() {
        assumeTrue(Files.isDirectory(SSD.resolve("GARE 2025")), "SSD /Volumes/AI not mounted");
        NasProperties nas = new NasProperties(Map.of("gare", new NasProperties.Share("ssd-test", SSD)),
                Map.of(NasMode.INDEX_READ, List.of(ROOT)), 0, Duration.ofSeconds(10), false, 20_000);
        NasReader reader = it.adprogetti.zio.edge.nas.NasTestFactory.reader(nas);
        DocsProperties props = new DocsProperties(List.of(ROOT), 0, 0, 0, 0, Duration.ofSeconds(60), 800, 100, 16, 0);
        PdfTextExtractor extractor = new PdfTextExtractor(props);

        List<String> pdfs = new ArrayList<>();
        walk(reader, ROOT, pdfs, 4000);
        assertThat(pdfs).as("PDF found").hasSizeGreaterThan(200);
        Collections.shuffle(pdfs, new java.util.Random(11));
        List<String> sample = pdfs.subList(0, Math.min(SAMPLE, pdfs.size()));

        Map<String, Integer> outcome = new TreeMap<>();
        List<Long> millis = new ArrayList<>();
        long chars = 0;
        long chunkCount = 0;
        int maxChunk = 0;
        for (String path : sample) {
            long start = System.nanoTime();
            try (InputStream in = reader.open(NasMode.INDEX_READ, path)) {
                PdfTextExtractor.Extracted e = extractor.extract(in);
                if (PdfTextExtractor.looksScanned(e)) {
                    outcome.merge("NO_TEXT", 1, Integer::sum);
                } else {
                    outcome.merge("TEXT", 1, Integer::sum);
                    chars += e.textChars();
                    List<Chunker.Chunk> chunks = Chunker.split(e.pages(), 800, 100);
                    chunkCount += chunks.size();
                    maxChunk = Math.max(maxChunk, chunks.stream().mapToInt(c -> c.content().length()).max().orElse(0));
                    assertThat(chunks).isNotEmpty();
                    assertThat(chunks).allSatisfy(c -> assertThat(c.content()).doesNotContain("\u0000"));
                }
            } catch (ExtractionException e) {
                outcome.merge(e.code(), 1, Integer::sum);
            } catch (java.io.IOException e) {
                outcome.merge("IO", 1, Integer::sum);
            }
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        Collections.sort(millis);
        System.out.printf("SSD TRIAL: sample=%d of %d PDFs, outcomes=%s, chars=%d, chunks=%d, maxChunkChars=%d, "
                + "median=%d ms, p95=%d ms, max=%d ms%n", sample.size(), pdfs.size(), outcome, chars, chunkCount, maxChunk,
                millis.get(millis.size() / 2), millis.get((int) (millis.size() * 0.95)), millis.get(millis.size() - 1));

        int withText = outcome.getOrDefault("TEXT", 0);
        assertThat(withText).as("share of PDFs with extractable text").isGreaterThanOrEqualTo((int) (sample.size() * 0.8));
        assertThat(outcome.getOrDefault("TIMEOUT", 0)).isLessThanOrEqualTo(2);
        assertThat(maxChunk).isLessThanOrEqualTo(800);
        assertThat(millis.get(millis.size() - 1)).as("slowest file").isLessThan(60_000);
    }

    private static void walk(NasReader reader, String folder, List<String> out, int cap) {
        if (out.size() >= cap) {
            return;
        }
        for (NasReader.Entry e : reader.list(NasMode.INDEX_READ, folder)) {
            String path = folder + "/" + e.name();
            if (e.directory()) {
                if (!e.name().startsWith(".") && !e.name().equals("#recycle") && !e.name().equals("@eaDir")) {
                    walk(reader, path, out, cap);
                }
            } else if (e.name().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
                out.add(path);
            }
        }
    }
}
