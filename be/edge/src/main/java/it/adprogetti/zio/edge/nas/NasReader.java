package it.adprogetti.zio.edge.nas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read access to the NAS. It cannot modify or delete anything; paths come out only as application paths. */
@Service
public class NasReader {

    public record Entry(String name, boolean directory, long size, Instant modifiedAt) {
    }

    private final NasPathResolver resolver;
    private final NasProperties props;

    NasReader(NasPathResolver resolver, NasProperties props) {
        this.resolver = resolver;
        this.props = props;
    }

    public List<Entry> list(NasMode mode, String logicalPath) {
        NasPathResolver.Resolved target = resolver.resolve(mode, logicalPath);
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target.path())) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (name.startsWith(".zio-")) {
                    continue; // our own temporary and marker files
                }
                if (entries.size() >= props.maxListEntries()) {
                    throw new NasException(NasException.Code.TOO_MANY_ENTRIES);
                }
                entries.add(entry(child));
            }
        } catch (java.nio.file.NotDirectoryException e) {
            throw new NasException(NasException.Code.INVALID_PATH);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new NasException(NasException.Code.NOT_FOUND);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new NasException(NasException.Code.NO_PERMISSION);
        } catch (IOException e) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
        entries.sort(java.util.Comparator.comparing(Entry::name));
        return entries;
    }

    public Entry metadata(NasMode mode, String logicalPath) {
        NasPathResolver.Resolved target = resolver.resolve(mode, logicalPath);
        try {
            return entry(target.path());
        } catch (java.nio.file.NoSuchFileException e) {
            throw new NasException(NasException.Code.NOT_FOUND);
        } catch (IOException e) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
    }

    /** Opens a file for reading. The caller closes the stream. */
    public InputStream open(NasMode mode, String logicalPath) {
        NasPathResolver.Resolved target = resolver.resolve(mode, logicalPath);
        if (Files.isDirectory(target.path())) {
            throw new NasException(NasException.Code.IS_DIRECTORY);
        }
        try {
            return Files.newInputStream(target.path(), LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new NasException(NasException.Code.NOT_FOUND);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new NasException(NasException.Code.NO_PERMISSION);
        } catch (IOException e) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
    }

    public String sha256(NasMode mode, String logicalPath) {
        try (InputStream in = open(mode, logicalPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0;) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Entry entry(Path path) throws IOException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class);
        return new Entry(path.getFileName().toString(), a.isDirectory(), a.isDirectory() ? 0 : a.size(),
                a.lastModifiedTime().toInstant());
    }
}
