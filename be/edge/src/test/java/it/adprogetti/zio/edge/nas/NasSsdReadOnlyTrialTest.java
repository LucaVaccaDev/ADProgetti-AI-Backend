package it.adprogetti.zio.edge.nas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Read-only trial of the NAS service on the external SSD (/Volumes/AI), before the real NAS is connected.
 * Skipped when the SSD is not mounted. Needs no Spring context and no database; it creates, modifies and
 * deletes nothing on the SSD. File and folder names are never printed, only counts and codes.
 */
class NasSsdReadOnlyTrialTest {

    private static final Path SSD = Path.of("/Volumes/AI");

    private static NasReader reader;

    @BeforeAll
    static void setUp() {
        assumeTrue(Files.isDirectory(SSD.resolve("GARE 2025")), "SSD /Volumes/AI not mounted");
        NasProperties props = new NasProperties(
                Map.of("gare", new NasProperties.Share("ssd-test", SSD)),
                Map.of(NasMode.INDEX_READ, List.of("gare:/GARE 2024", "gare:/GARE 2025")),
                0, Duration.ofSeconds(10), false, 20_000);
        NasAvailability availability = new NasAvailability(props, Clock.systemUTC());
        reader = new NasReader(new NasPathResolver(props, availability), props);
    }

    private static NasException.Code codeOf(Runnable action) {
        try {
            action.run();
        } catch (NasException e) {
            return e.code();
        }
        return null;
    }

    /** Up to {@code limit} files below a logical folder, found through the reader (not through java.nio directly). */
    private static List<String> sampleFiles(String folder, int limit) {
        List<String> files = new ArrayList<>();
        List<String> pending = new ArrayList<>(List.of(folder));
        while (!pending.isEmpty() && files.size() < limit) {
            String dir = pending.remove(0);
            for (NasReader.Entry e : reader.list(NasMode.INDEX_READ, dir)) {
                (e.directory() ? pending : files).add(dir + "/" + e.name());
            }
        }
        return files.subList(0, Math.min(limit, files.size()));
    }

    private static byte[] sha(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0;) {
                d.update(buffer, 0, n);
            }
            return d.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void listsBothYearFoldersAndFindsFiles() {
        assertThat(reader.list(NasMode.INDEX_READ, "gare:/GARE 2024")).isNotEmpty();
        assertThat(reader.list(NasMode.INDEX_READ, "gare:/GARE 2025")).isNotEmpty();
        assertThat(sampleFiles("gare:/GARE 2025", 200)).hasSizeGreaterThan(50);
    }

    @Test
    void readsFilesWithUnusualNamesAndChecksTheirHash() throws IOException {
        List<String> files = sampleFiles("gare:/GARE 2025", 300);
        List<String> unusual = files.stream().filter(f -> !f.matches("[A-Za-z0-9/_:.-]+")).toList();
        assertThat(unusual).as("sample has names with spaces, accents or symbols").isNotEmpty();
        int checked = 0;
        for (String f : unusual.subList(0, Math.min(15, unusual.size()))) {
            NasReader.Entry meta = reader.metadata(NasMode.INDEX_READ, f);
            if (meta.size() > 50L * 1024 * 1024) {
                continue; // keep the trial quick
            }
            Path direct = SSD.resolve(f.substring("gare:/".length()));
            String expected = HexFormat.of().formatHex(
                    sha(direct));
            assertThat(reader.sha256(NasMode.INDEX_READ, f)).isEqualTo(expected);
            checked++;
        }
        assertThat(checked).isGreaterThan(0);
    }

    @Test
    void streamsTheLargestSampledFileWithoutLoadingItAll() throws IOException {
        String biggest = null;
        long size = -1;
        for (String f : sampleFiles("gare:/GARE 2024", 400)) {
            long s = reader.metadata(NasMode.INDEX_READ, f).size();
            if (s > size && s < 500L * 1024 * 1024) {
                size = s;
                biggest = f;
            }
        }
        assertThat(biggest).isNotNull();
        long read = 0;
        try (InputStream in = reader.open(NasMode.INDEX_READ, biggest)) {
            byte[] buffer = new byte[256 * 1024];
            for (int n; (n = in.read(buffer)) > 0;) {
                read += n;
            }
        }
        assertThat(read).isEqualTo(size);
    }

    @Test
    void everythingOutsideTheAllowedFoldersIsRefused() {
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "gare:/"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "gare:/ollama"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "gare:/GARE 2026"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "archivio:/"))).isIn(
                NasException.Code.INVALID_PATH, NasException.Code.MODE_FORBIDDEN);
    }

    @Test
    void traversalAndMalformedPathsAreRefused() {
        for (String bad : List.of("gare:/GARE 2025/../ollama", "gare:/GARE 2025/../../etc/passwd",
                "gare:/GARE 2025/%2e%2e/ollama", "gare:/GARE 2025\\..\\ollama", "gare:/GARE 2025/\u0000x",
                "/Volumes/AI/ollama", "gare:/GARE 2025/./../GARE 2026", "../ollama", "")) {
            NasException.Code code = codeOf(() -> reader.list(NasMode.INDEX_READ, bad));
            assertThat(code).as("path %s", bad.replace("\u0000", "NUL")).isNotNull();
        }
    }

    @Test
    void writingModesAreNotAvailable() {
        assertThat(codeOf(() -> reader.list(NasMode.PROTOCOL_WRITE, "gare:/GARE 2025"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> reader.list(NasMode.TENDER_WRITE, "gare:/GARE 2025"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
    }

    @Test
    void aMissingFileIsNotFound() {
        assertThat(codeOf(() -> reader.metadata(NasMode.INDEX_READ, "gare:/GARE 2025/__missing__.pdf")))
                .isEqualTo(NasException.Code.NOT_FOUND);
    }
}
