package it.adprogetti.zio.edge.nas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import it.adprogetti.zio.contracts.message.ServiceStatus;
import it.adprogetti.zio.edge.support.EdgeIntegrationTestBase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NasServiceTest extends EdgeIntegrationTestBase {

    @Autowired NasReader reader;
    @Autowired NasWriter writer;
    @Autowired NasAvailability availability;
    @Autowired NasHealthProbe probe;
    @Autowired JdbcTemplate jdbc;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static NasException.Code codeOf(Runnable action) {
        try {
            action.run();
        } catch (NasException e) {
            return e.code();
        }
        return null;
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // --- reading ---------------------------------------------------------------------------------

    @Test
    void readingListsOpensAndHashesFilesButNeverEscapesTheRoot() throws IOException {
        String dir = "doc-" + unique();
        Files.createDirectories(NAS.resolve("documenti").resolve(dir));
        Files.writeString(NAS.resolve("documenti").resolve(dir).resolve("a.txt"), "ciao");
        Files.writeString(NAS.resolve("documenti").resolve(dir).resolve(".zio-part-x"), "hidden");

        List<NasReader.Entry> entries = reader.list(NasMode.INDEX_READ, "documenti:/" + dir);
        assertThat(entries).extracting(NasReader.Entry::name).containsExactly("a.txt");
        assertThat(reader.metadata(NasMode.INDEX_READ, "documenti:/" + dir + "/a.txt").size()).isEqualTo(4);
        try (InputStream in = reader.open(NasMode.INDEX_READ, "documenti:/" + dir + "/a.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ciao");
        }
        assertThat(reader.sha256(NasMode.INDEX_READ, "documenti:/" + dir + "/a.txt")).isEqualTo("b133a0c0e9bee3be20163d2ad31d6248db292aa6dcb1ee087a2aa50e0fc75ae2");
        assertThat(codeOf(() -> reader.open(NasMode.INDEX_READ, "documenti:/" + dir))).isEqualTo(NasException.Code.IS_DIRECTORY);
        assertThat(codeOf(() -> reader.metadata(NasMode.INDEX_READ, "documenti:/" + dir + "/missing.txt")))
                .isEqualTo(NasException.Code.NOT_FOUND);
    }

    @Test
    void theTemporaryShareAndUnknownSharesCannotBeReached() {
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "temporanea:/"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "temporanea:/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "nope:/"))).isEqualTo(NasException.Code.INVALID_PATH);
    }

    @Test
    void aSymbolicLinkLeavingTheRootIsRefusedEvenForReading() throws IOException {
        String name = "link-" + unique();
        Path link = NAS.resolve("archivio").resolve(name);
        Files.createSymbolicLink(link, NAS.resolve("outside"));
        try {
            assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "archivio:/" + name))).isEqualTo(NasException.Code.INVALID_PATH);
            assertThat(codeOf(() -> reader.open(NasMode.INDEX_READ, "archivio:/" + name + "/secret.txt")))
                    .isEqualTo(NasException.Code.INVALID_PATH);
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void aSymbolicLinkInsideTheRootIsFollowedAndADanglingOneIsRefused() throws IOException {
        String real = "real-" + unique();
        String inside = "inside-" + unique();
        String dangling = "dangling-" + unique();
        Files.createDirectories(NAS.resolve("archivio").resolve(real));
        Files.writeString(NAS.resolve("archivio").resolve(real).resolve("f.txt"), "x");
        Files.createSymbolicLink(NAS.resolve("archivio").resolve(inside), NAS.resolve("archivio").resolve(real));
        Files.createSymbolicLink(NAS.resolve("archivio").resolve(dangling), NAS.resolve("outside/does-not-exist"));
        try {
            assertThat(reader.list(NasMode.INDEX_READ, "archivio:/" + inside)).extracting(NasReader.Entry::name).containsExactly("f.txt");
            assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "archivio:/" + dangling))).isEqualTo(NasException.Code.INVALID_PATH);
        } finally {
            Files.deleteIfExists(NAS.resolve("archivio").resolve(inside));
            Files.deleteIfExists(NAS.resolve("archivio").resolve(dangling));
        }
    }

    @Test
    void aShareThatIsNotMountedIsUnavailable() {
        assertThat(codeOf(() -> reader.list(NasMode.INDEX_READ, "fantasma:/"))).isEqualTo(NasException.Code.UNAVAILABLE);
    }

    // --- writing: modes and roots ------------------------------------------------------------------

    @Test
    void readingModeCanNeverWrite() {
        assertThat(codeOf(() -> writer.write(NasMode.INDEX_READ, "archivio:/Protocolli/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.createDirectories(NasMode.INDEX_READ, "archivio:/Protocolli/d")))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
    }

    @Test
    void protocolWritingStaysInsideItsRoot() {
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Altro/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli2/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli/../Altro/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.INVALID_PATH);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "gare:/G1/x.txt", bytes("x"), null)))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli", bytes("x"), null)))
                .isEqualTo(NasException.Code.INVALID_PATH); // the root itself is never a file
    }

    @Test
    void aLinkInsideTheProtocolRootCannotCarryAWriteOutside() throws IOException {
        String name = "out-" + unique();
        Files.createDirectories(NAS.resolve("archivio/Protocolli"));
        Path link = NAS.resolve("archivio/Protocolli").resolve(name);
        Files.createSymbolicLink(link, NAS.resolve("outside"));
        try {
            assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli/" + name + "/pwned.txt",
                    bytes("x"), null))).isEqualTo(NasException.Code.INVALID_PATH);
            assertThat(NAS.resolve("outside/pwned.txt")).doesNotExist();
        } finally {
            Files.deleteIfExists(link);
        }
    }

    // --- writing: safety -----------------------------------------------------------------------------

    @Test
    void aFileIsWrittenAtomicallyVerifiedAndNeverOverwritten() throws IOException {
        String path = "archivio:/Protocolli/2026/" + unique() + "/lettera.pdf";
        NasWriter.WriteResult r = writer.write(NasMode.PROTOCOL_WRITE, path, bytes("contenuto"), null);
        assertThat(r.size()).isEqualTo(9);
        assertThat(r.sha256()).isEqualTo(reader.sha256(NasMode.PROTOCOL_WRITE, path));
        assertThat(r.logicalPath()).isEqualTo(path);

        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, path, bytes("altro"), null)))
                .isEqualTo(NasException.Code.COLLISION);
        try (InputStream in = reader.open(NasMode.PROTOCOL_WRITE, path)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("contenuto");
        }
        Path parent = NAS.resolve("archivio/Protocolli/2026").resolve(path.split("/")[3]);
        try (Stream<Path> files = Files.list(parent)) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly("lettera.pdf"); // no leftovers
        }
    }

    @Test
    void aWrongHashOrSizeLeavesNothingBehind() throws IOException {
        String dir = unique();
        String path = "archivio:/Protocolli/" + dir + "/a.txt";
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, path, bytes("abc"), "0".repeat(64))))
                .isEqualTo(NasException.Code.INTEGRITY_FAILED);
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, path,
                new ByteArrayInputStream(bytes("abc")), 99, null))).isEqualTo(NasException.Code.INTEGRITY_FAILED);
        try (Stream<Path> files = Files.list(NAS.resolve("archivio/Protocolli").resolve(dir))) {
            assertThat(files.toList()).isEmpty();
        }
        assertThat(NAS.resolve("archivio/Protocolli").resolve(dir).resolve("a.txt")).doesNotExist();
    }

    @Test
    void aFailingSourceLeavesNoPartialFile() throws IOException {
        String dir = unique();
        InputStream broken = new InputStream() {
            int n;
            @Override public int read() throws IOException { throw new IOException("disk gone"); }
            @Override public int read(byte[] b, int o, int l) throws IOException {
                if (n++ == 0) { b[o] = 1; return 1; }
                throw new IOException("disk gone");
            }
        };
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli/" + dir + "/b.bin", broken, -1, null)))
                .isEqualTo(NasException.Code.WRITE_FAILED);
        try (Stream<Path> files = Files.list(NAS.resolve("archivio/Protocolli").resolve(dir))) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void notEnoughFreeSpaceRefusesTheWriteBeforeStarting() {
        assertThat(codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli/" + unique() + "/big.bin",
                new ByteArrayInputStream(new byte[0]), Long.MAX_VALUE / 2, null)))
                .isEqualTo(NasException.Code.INSUFFICIENT_SPACE);
    }

    @Test
    void sameNameFromTwoThreadsGivesExactlyOneWinner() throws Exception {
        String path = "archivio:/Protocolli/race-" + unique() + "/x.txt";
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<NasException.Code>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                final int n = i;
                results.add(pool.submit(() -> codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, path, bytes("w" + n), null))));
            }
            int wins = 0;
            for (var f : results) {
                NasException.Code c = f.get();
                if (c == null) {
                    wins++;
                } else {
                    assertThat(c).isEqualTo(NasException.Code.COLLISION);
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void movingNeverOverwritesAndKeepsInsideTheRoot() throws IOException {
        String d = unique();
        String from = "archivio:/Protocolli/" + d + "/a.txt";
        String to = "archivio:/Protocolli/" + d + "/ANNULLATI/a.txt";
        writer.write(NasMode.PROTOCOL_WRITE, from, bytes("x"), null);
        writer.move(NasMode.PROTOCOL_WRITE, from, to);
        assertThat(codeOf(() -> reader.metadata(NasMode.PROTOCOL_WRITE, from))).isEqualTo(NasException.Code.NOT_FOUND);
        assertThat(reader.metadata(NasMode.PROTOCOL_WRITE, to).size()).isEqualTo(1);

        writer.write(NasMode.PROTOCOL_WRITE, from, bytes("y"), null);
        assertThat(codeOf(() -> writer.move(NasMode.PROTOCOL_WRITE, from, to))).isEqualTo(NasException.Code.COLLISION);
        assertThat(codeOf(() -> writer.move(NasMode.PROTOCOL_WRITE, from, "archivio:/Documenti/a.txt")))
                .isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(codeOf(() -> writer.move(NasMode.PROTOCOL_WRITE, "archivio:/Protocolli/" + d + "/none", to + "2")))
                .isEqualTo(NasException.Code.NOT_FOUND);
    }

    @Test
    void thereIsNoWayToDelete() {
        assertThat(java.util.Arrays.stream(NasWriter.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.toLowerCase().contains("delete") && !n.startsWith("lambda$"))
                .noneMatch(n -> n.equals("remove"));
        assertThat(java.util.Arrays.stream(NasReader.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.toLowerCase().contains("delete") || n.toLowerCase().contains("write"));
    }

    // --- tenders -------------------------------------------------------------------------------------

    @Test
    void aNewTenderFolderCanReceiveApprovedDocumentsButAHistoricalOneCannot() throws IOException {
        String tender = "G_" + unique();
        writer.createTenderFolder("gare:/" + tender);
        writer.write(NasMode.TENDER_WRITE, "gare:/" + tender + "/offerta/tecnica.pdf", bytes("pdf"), null);
        assertThat(reader.list(NasMode.TENDER_WRITE, "gare:/" + tender)).extracting(NasReader.Entry::name)
                .containsExactly("offerta"); // the marker is hidden
        assertThat(codeOf(() -> writer.createTenderFolder("gare:/" + tender))).isEqualTo(NasException.Code.HISTORICAL_TENDER);

        String historical = "STORICA_" + unique();
        Files.createDirectories(NAS.resolve("gare").resolve(historical));
        Files.writeString(NAS.resolve("gare").resolve(historical).resolve("vecchio.pdf"), "old");
        assertThat(codeOf(() -> writer.createTenderFolder("gare:/" + historical))).isEqualTo(NasException.Code.HISTORICAL_TENDER);
        assertThat(codeOf(() -> writer.write(NasMode.TENDER_WRITE, "gare:/" + historical + "/nuovo.pdf", bytes("x"), null)))
                .isEqualTo(NasException.Code.HISTORICAL_TENDER);
        assertThat(codeOf(() -> writer.move(NasMode.TENDER_WRITE, "gare:/" + tender + "/offerta/tecnica.pdf",
                "gare:/" + tender + "/offerta/b.pdf"))).isEqualTo(NasException.Code.MODE_FORBIDDEN);
        assertThat(NAS.resolve("gare").resolve(historical).resolve("vecchio.pdf")).hasContent("old");
    }

    @Test
    void tenderFoldersMustBeDirectChildrenOfTheRoot() {
        assertThat(codeOf(() -> writer.createTenderFolder("gare:/a/b"))).isEqualTo(NasException.Code.INVALID_PATH);
        assertThat(codeOf(() -> writer.createTenderFolder("gare:/"))).isEqualTo(NasException.Code.INVALID_PATH);
    }

    // --- audit and health ---------------------------------------------------------------------------

    @Test
    void everyWriteAndEveryRefusalIsAuditedWithoutContents() {
        String path = "archivio:/Protocolli/" + unique() + "/audit.txt";
        int before = jdbc.queryForObject("select count(*) from audit_log where action like 'NAS_%'", Integer.class);
        writer.write(NasMode.PROTOCOL_WRITE, path, bytes("segreto-del-cliente"), null);
        codeOf(() -> writer.write(NasMode.PROTOCOL_WRITE, path, bytes("x"), null));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action like 'NAS_%'", Integer.class))
                .isEqualTo(before + 2);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where detail like '%segreto%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'NAS_WRITE' and outcome = 'FAILURE' "
                + "and detail = 'COLLISION'", Integer.class)).isPositive();
    }

    @Test
    void healthReflectsWhichSharesAreReallyThere() {
        availability.refresh();
        ServiceStatus status = probe.probe();
        assertThat(status.name()).isEqualTo("nas");
        assertThat(status.state()).isEqualTo(ServiceStatus.State.DEGRADED); // "fantasma" is not mounted
        assertThat(status.detail()).isEqualTo("4_OF_5");
        assertThat(availability.status("archivio").available()).isTrue();
        assertThat(availability.status("fantasma").available()).isFalse();
    }

    @Test
    void mountPointDetectionTellsAMountFromAPlainFolder() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/Volumes/AI")), "needs a real mounted volume");
        assertThat(NasAvailability.isMountPoint(Path.of("/Volumes/AI"))).isTrue();
        assertThat(NasAvailability.isMountPoint(NAS.resolve("archivio"))).isFalse();
    }
}
