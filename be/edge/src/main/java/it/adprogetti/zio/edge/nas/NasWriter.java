package it.adprogetti.zio.edge.nas;

import it.adprogetti.zio.edge.audit.EdgeAudit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Controlled writing to the NAS (capitolato 5.2). Rules that hold for every operation: only in a write mode and
 * only inside that mode's roots; the share must be really available with enough free space; an existing file is
 * never overwritten; a file is written to a temporary name, verified by hash and then renamed. There is no
 * delete: a cancellation is a move, decided by the module that owns the rules. Every operation is audited.
 */
@Service
public class NasWriter {

    public record WriteResult(String logicalPath, long size, String sha256) {
    }

    static final String TENDER_MARKER = ".zio-tender";

    private final NasPathResolver resolver;
    private final NasAvailability availability;
    private final NasProperties props;
    private final EdgeAudit audit;
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    NasWriter(NasPathResolver resolver, NasAvailability availability, NasProperties props, EdgeAudit audit) {
        this.resolver = resolver;
        this.availability = availability;
        this.props = props;
        this.audit = audit;
    }

    public WriteResult write(NasMode mode, String logicalPath, byte[] content, String expectedSha256) {
        return write(mode, logicalPath, new java.io.ByteArrayInputStream(content), content.length, expectedSha256);
    }

    /**
     * @param sizeHint expected size in bytes, or -1 if unknown; if given, the result must match it
     * @param expectedSha256 hex digest the content must have, or null
     */
    public WriteResult write(NasMode mode, String logicalPath, InputStream content, long sizeHint,
            String expectedSha256) {
        return audited("NAS_WRITE", mode, logicalPath, () -> doWrite(mode, logicalPath, content, sizeHint, expectedSha256));
    }

    public void createDirectories(NasMode mode, String logicalPath) {
        audited("NAS_MKDIR", mode, logicalPath, () -> {
            NasPathResolver.Resolved target = prepare(mode, logicalPath, 0);
            requireWritableSubject(target);
            try {
                if (Files.exists(target.path(), LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target.path())) {
                    throw new NasException(NasException.Code.COLLISION);
                }
                Files.createDirectories(target.path());
                recheckInside(target, target.path());
            } catch (IOException e) {
                throw mapIo(e);
            }
            return null;
        });
    }

    /**
     * Creates the final folder of a tender after approval. It fails if the folder exists (historical tender) and
     * leaves a marker so that later approved documents can be archived there and nowhere else.
     */
    public void createTenderFolder(String logicalPath) {
        audited("NAS_TENDER_FOLDER", NasMode.TENDER_WRITE, logicalPath, () -> {
            NasPathResolver.Resolved target = prepare(NasMode.TENDER_WRITE, logicalPath, 0);
            if (target.logical().segments().size() != target.rootSegmentCount() + 1) {
                throw new NasException(NasException.Code.INVALID_PATH);
            }
            try {
                Files.createDirectory(target.path());
                Files.writeString(target.path().resolve(TENDER_MARKER), "created by the Edge after approval\n",
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException e) {
                throw new NasException(NasException.Code.HISTORICAL_TENDER);
            } catch (IOException e) {
                throw mapIo(e);
            }
            return null;
        });
    }

    /** Renames inside the same mode. Never overwrites, never used for tenders, never touches our own files. */
    public void move(NasMode mode, String fromPath, String toPath) {
        audited("NAS_MOVE", mode, fromPath + " -> " + toPath, () -> {
            if (mode == NasMode.TENDER_WRITE) {
                throw new NasException(NasException.Code.MODE_FORBIDDEN);
            }
            NasPathResolver.Resolved from = prepare(mode, fromPath, 0);
            NasPathResolver.Resolved to = prepare(mode, toPath, 0);
            requireWritableSubject(from);
            requireWritableSubject(to);
            if (!Files.exists(from.path(), LinkOption.NOFOLLOW_LINKS)) {
                throw new NasException(NasException.Code.NOT_FOUND);
            }
            String key = to.path().toString();
            if (!inProgress.add(key)) {
                throw new NasException(NasException.Code.COLLISION);
            }
            try {
                Files.createDirectories(to.path().getParent());
                recheckInside(to, to.path().getParent());
                Files.move(from.path(), to.path());
            } catch (FileAlreadyExistsException e) {
                throw new NasException(NasException.Code.COLLISION);
            } catch (IOException e) {
                throw mapIo(e);
            } finally {
                inProgress.remove(key);
            }
            return null;
        });
    }

    // --- internals --------------------------------------------------------------------------------

    private WriteResult doWrite(NasMode mode, String logicalPath, InputStream content, long sizeHint,
            String expectedSha256) {
        NasPathResolver.Resolved target = prepare(mode, logicalPath, sizeHint);
        requireWritableSubject(target);
        if (mode == NasMode.TENDER_WRITE) {
            requireOpenTender(target);
        }
        String key = target.path().toString();
        if (!inProgress.add(key)) {
            throw new NasException(NasException.Code.COLLISION);
        }
        Path temp = null;
        boolean moved = false;
        try {
            if (Files.exists(target.path(), LinkOption.NOFOLLOW_LINKS)) {
                throw new NasException(NasException.Code.COLLISION);
            }
            Path parent = target.path().getParent();
            Files.createDirectories(parent);
            recheckInside(target, parent);
            temp = parent.resolve(".zio-part-" + UUID.randomUUID());
            Copied copied = copyTo(temp, content);
            if (sizeHint >= 0 && copied.size() != sizeHint
                    || expectedSha256 != null && !expectedSha256.equalsIgnoreCase(copied.sha256())) {
                throw new NasException(NasException.Code.INTEGRITY_FAILED);
            }
            Files.move(temp, target.path());
            moved = true;
            temp = null;
            if (!copied.sha256().equals(hashOf(target.path()))) {
                Files.deleteIfExists(target.path()); // we just created this file, and it is damaged
                moved = false;
                throw new NasException(NasException.Code.INTEGRITY_FAILED);
            }
            return new WriteResult(target.logical().text(), copied.size(), copied.sha256());
        } catch (FileAlreadyExistsException e) {
            throw new NasException(NasException.Code.COLLISION);
        } catch (IOException e) {
            throw mapIo(e);
        } finally {
            inProgress.remove(key);
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // a leftover .zio-part file is harmless and hidden from listings
                }
            }
        }
    }

    private NasPathResolver.Resolved prepare(NasMode mode, String logicalPath, long sizeHint) {
        if (!mode.writes()) {
            throw new NasException(NasException.Code.MODE_FORBIDDEN);
        }
        NasPathResolver.Resolved target = resolver.resolve(mode, logicalPath);
        long free = availability.requireAvailable(target.logical().share());
        if (free - Math.max(0, sizeHint) < props.minFreeBytes()) {
            throw new NasException(NasException.Code.INSUFFICIENT_SPACE);
        }
        return target;
    }

    /** The mode root itself is never the subject of a write. */
    private static void requireWritableSubject(NasPathResolver.Resolved target) {
        if (target.isRoot()) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
    }

    private void requireOpenTender(NasPathResolver.Resolved target) {
        if (target.logical().segments().size() <= target.rootSegmentCount() + 1) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        Path tenderDir = target.rootReal().resolve(target.logical().segments().get(target.rootSegmentCount()));
        if (!Files.isRegularFile(tenderDir.resolve(TENDER_MARKER), LinkOption.NOFOLLOW_LINKS)) {
            throw new NasException(NasException.Code.HISTORICAL_TENDER);
        }
    }

    /** Guards against a link swapped in between the check and the use. */
    private static void recheckInside(NasPathResolver.Resolved target, Path created) {
        if (!NasPathResolver.realize(created).startsWith(target.rootReal())) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
    }

    private record Copied(long size, String sha256) {
    }

    private static Copied copyTo(Path temp, InputStream in) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0;) {
                digest.update(buffer, 0, n);
                ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, n);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                total += n;
            }
            channel.force(true);
        }
        return new Copied(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static String hashOf(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0;) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NasException mapIo(IOException e) {
        if (e instanceof java.nio.file.AccessDeniedException) {
            return new NasException(NasException.Code.NO_PERMISSION);
        }
        if (e instanceof java.nio.file.FileSystemException f && f.getReason() != null
                && f.getReason().toLowerCase().contains("no space")) {
            return new NasException(NasException.Code.INSUFFICIENT_SPACE);
        }
        return new NasException(NasException.Code.WRITE_FAILED);
    }

    private interface Operation<T> {
        T run();
    }

    /** Runs the operation and records it in the Edge audit, whatever the outcome. No file contents are recorded. */
    private <T> T audited(String action, NasMode mode, String subject, Operation<T> operation) {
        try {
            T result = operation.run();
            String detail = result instanceof WriteResult w ? "sha256=" + w.sha256() + " size=" + w.size() : null;
            audit.record(action, mode + " " + subject, EdgeAudit.SUCCESS, detail);
            return result;
        } catch (NasException e) {
            audit.record(action, mode + " " + subject, EdgeAudit.FAILURE, e.code().name());
            throw e;
        }
    }
}
