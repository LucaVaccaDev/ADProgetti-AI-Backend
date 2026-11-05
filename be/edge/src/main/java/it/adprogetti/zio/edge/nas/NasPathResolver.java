package it.adprogetti.zio.edge.nas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The only place that turns an application path into a file-system path (CLAUDE.md, security rule 8). It
 * normalises, resolves symbolic links with {@code toRealPath} and checks that the real result stays inside a root
 * allowed for the requested mode. Nothing else in the Edge opens a NAS path on its own.
 */
@Component
class NasPathResolver {

    /**
     * @param path real path to use (the nearest existing ancestor is resolved, the rest appended)
     * @param rootSegmentCount how many segments of the logical path belong to the mode root
     * @param rootReal the real path of the mode root
     */
    record Resolved(NasMode mode, LogicalPath logical, Path path, Path rootReal, int rootSegmentCount) {
        boolean isRoot() {
            return logical.segments().size() == rootSegmentCount;
        }
    }

    private final NasProperties props;
    private final NasAvailability availability;

    NasPathResolver(NasProperties props, NasAvailability availability) {
        this.props = props;
        this.availability = availability;
    }

    Resolved resolve(NasMode mode, String text) {
        LogicalPath logical = LogicalPath.parse(text);
        NasProperties.Share share = props.shares().get(logical.share());
        if (share == null || share.mount() == null) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        List<String> root = allowedRoot(mode, logical);
        availability.requireAvailable(logical.share());
        return availability.call(() -> {
            Path mount = share.mount().toRealPath();
            Path rootReal = realize(append(mount, root));
            Path real = realize(append(mount, logical.segments()));
            if (!rootReal.startsWith(mount) || !real.startsWith(rootReal)) {
                throw new NasException(NasException.Code.INVALID_PATH);
            }
            return new Resolved(mode, logical, real, rootReal, root.size());
        });
    }

    /** The configured root of this mode that contains the path, or MODE_FORBIDDEN. */
    private List<String> allowedRoot(NasMode mode, LogicalPath logical) {
        for (String configured : props.modes().getOrDefault(mode, List.of())) {
            LogicalPath root = LogicalPath.parse(configured);
            if (root.share().equals(logical.share()) && logical.startsWith(root.segments())) {
                return root.segments();
            }
        }
        throw new NasException(NasException.Code.MODE_FORBIDDEN);
    }

    private static Path append(Path base, List<String> segments) {
        Path p = base;
        for (String s : segments) {
            p = p.resolve(s);
        }
        return p;
    }

    /**
     * Resolves symbolic links of the part that exists and appends the part that does not exist yet. A dangling
     * link is an error: it could point anywhere and be created later.
     */
    static Path realize(Path wanted) {
        Path existing = wanted;
        Deque<Path> missing = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.push(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException e) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        for (Path name : missing) {
            real = real.resolve(name);
        }
        return real;
    }
}
