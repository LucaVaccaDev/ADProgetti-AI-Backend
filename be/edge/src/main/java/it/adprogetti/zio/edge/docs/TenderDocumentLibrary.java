package it.adprogetti.zio.edge.docs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The tender documents kept on the Edge until the tender expires (D-30): {@code <library>/<tender number>/<file>}.
 * Every path is built here from a validated tender number and a sanitised file name, and checked to stay inside the
 * library, so no caller can reach another place. Only real PDFs are stored (by their first bytes). The rows in
 * {@code tender_document} describe the files; the files go when the tender expires or disappears ({@link #purge()}).
 */
@Service
public class TenderDocumentLibrary {

    public record Doc(UUID id, String notice, String fileName, long sizeBytes, String origin, java.time.Instant storedAt) {
    }

    private static final Pattern NOTICE = Pattern.compile("[A-Za-z0-9._-]{1,20}");
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._ ()+-]");

    private final TenderDocsProperties props;
    private final JdbcClient jdbc;
    private final Clock clock;

    TenderDocumentLibrary(TenderDocsProperties props, JdbcClient jdbc, Clock clock) {
        this.props = props;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The PDF itself, or the PDF enclosed in a signed envelope (.p7m); empty for anything else. */
    public static Optional<byte[]> asPdf(byte[] data) {
        if (P7mUnwrapper.isPdf(data)) {
            return Optional.of(data);
        }
        return P7mUnwrapper.unwrap(data).filter(P7mUnwrapper::isPdf);
    }

    public long maxBytes() {
        return props.maxPdfBytes();
    }

    /** Keeps a PDF for a known tender. @return the stored document, or empty when it is not a PDF, too big or already there. */
    public Optional<Doc> store(String notice, String rawName, byte[] pdf, String origin, String sourceUrl) {
        if (!NOTICE.matcher(notice).matches() || pdf.length < 100 || pdf.length > props.maxPdfBytes() || !P7mUnwrapper.isPdf(pdf)) {
            return Optional.empty();
        }
        String sha = sha256(pdf);
        if (exists(notice, sha) || !tenderExists(notice)) {
            return Optional.empty();
        }
        try {
            Path dir = dir(notice);
            Files.createDirectories(dir);
            String name = freeName(dir, cleanName(rawName));
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(dir)) {
                return Optional.empty();
            }
            Path tmp = Files.createTempFile(dir, ".tmp-", ".part");
            Files.write(tmp, pdf);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    insert into tender_document (id, notice_number, file_name, size_bytes, sha256, origin, source_url, stored_at)
                    values (:i, :n, :f, :s, :h, :o, :u, :t) on conflict (notice_number, sha256) do nothing""")
                    .param("i", id).param("n", notice).param("f", name).param("s", (long) pdf.length).param("h", sha)
                    .param("o", origin).param("u", sourceUrl).param("t", Timestamp.from(clock.instant())).update();
            return Optional.of(new Doc(id, notice, name, pdf.length, origin, clock.instant()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** True when this very document (same content) is already kept for the tender. */
    public boolean has(String notice, byte[] pdf) {
        return exists(notice, sha256(pdf));
    }

    public List<Doc> list(String notice) {
        return jdbc.sql("select id, notice_number, file_name, size_bytes, origin, stored_at from tender_document "
                + "where notice_number = :n order by stored_at, file_name").param("n", notice)
                .query((rs, i) -> new Doc(rs.getObject("id", UUID.class), rs.getString("notice_number"),
                        rs.getString("file_name"), rs.getLong("size_bytes"), rs.getString("origin"),
                        rs.getTimestamp("stored_at").toInstant())).list();
    }

    public Optional<Doc> find(String notice, UUID id) {
        return list(notice).stream().filter(d -> d.id().equals(id)).findFirst();
    }

    /** The file of a stored document (never outside the library), or empty when it is gone. */
    public Optional<Path> file(Doc doc) {
        if (!NOTICE.matcher(doc.notice()).matches()) {
            return Optional.empty();
        }
        Path dir = dir(doc.notice());
        Path p = dir.resolve(doc.fileName()).normalize();
        return p.startsWith(dir) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) ? Optional.of(p) : Optional.empty();
    }

    /**
     * Removes the documents of tenders that are past their deadline or no longer exist: files first, then the rows of
     * the expired ones (the others go with their tender). @return the number of tender folders removed.
     */
    public int purge() {
        jdbc.sql("delete from tender_document where notice_number in (select notice_number from tender "
                + "where deadline_at is not null and deadline_at <= :now)").param("now", Timestamp.from(clock.instant())).update();
        if (!Files.isDirectory(props.library(), LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(props.library())) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) && !keeps(name)) {
                    deleteTree(p);
                    removed++;
                }
            }
        } catch (IOException e) {
            // the next run tries again
        }
        return removed;
    }

    /** A folder stays while its tender is open and has at least a row. */
    private boolean keeps(String notice) {
        return NOTICE.matcher(notice).matches() && jdbc.sql("""
                select count(*) from tender_document d join tender t using (notice_number)
                where d.notice_number = :n and (t.deadline_at is null or t.deadline_at > :now)""")
                .param("n", notice).param("now", Timestamp.from(clock.instant())).query(Integer.class).single() > 0;
    }

    private Path dir(String notice) {
        return props.library().toAbsolutePath().normalize().resolve(notice).normalize();
    }

    private boolean exists(String notice, String sha) {
        return jdbc.sql("select count(*) from tender_document where notice_number = :n and sha256 = :h")
                .param("n", notice).param("h", sha).query(Integer.class).single() > 0;
    }

    private boolean tenderExists(String notice) {
        return jdbc.sql("select count(*) from tender where notice_number = :n").param("n", notice).query(Integer.class)
                .single() > 0;
    }

    /** Last path segment only, letters digits and a few signs, always ending in .pdf, at most 120 characters. */
    static String cleanName(String raw) {
        String base = raw == null ? "documento" : raw.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".p7m")) {
            base = base.substring(0, base.length() - 4);
        }
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        base = UNSAFE.matcher(base).replaceAll("_").replaceAll("\\.{2,}", ".").replaceAll("^[ .]+", "").trim();
        if (base.isEmpty()) {
            base = "documento";
        }
        return (base.length() > 120 ? base.substring(0, 120) : base) + ".pdf";
    }

    private static String freeName(Path dir, String name) {
        String stem = name.substring(0, name.length() - 4);
        String candidate = name;
        for (int i = 2; Files.exists(dir.resolve(candidate)); i++) {
            candidate = stem + " (" + i + ").pdf";
        }
        return candidate;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var s = Files.walk(root)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
