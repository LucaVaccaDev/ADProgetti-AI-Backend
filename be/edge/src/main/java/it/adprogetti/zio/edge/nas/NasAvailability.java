package it.adprogetti.zio.edge.nas;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Availability of the shares. A hung SMB mount blocks any file call forever, so every check runs on a small
 * bounded pool with a timeout: the caller gets UNAVAILABLE instead of a stuck thread. A mount point that is not
 * really mounted (an empty local folder) counts as unavailable, otherwise a write would land on the local disk.
 */
@Component
public class NasAvailability {

    private static final Logger log = LoggerFactory.getLogger(NasAvailability.class);

    public record ShareStatus(boolean available, long freeBytes, Instant checkedAt) {
    }

    private final NasProperties props;
    private final Clock clock;
    private final Map<String, ShareStatus> cache = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "edge-nas-io");
                t.setDaemon(true);
                return t;
            });

    NasAvailability(NasProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Runs a file-system task with a time limit. */
    <T> T call(Callable<T> task) {
        Future<T> future;
        try {
            future = pool.submit(task);
        } catch (RejectedExecutionException e) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
        try {
            return future.get(props.availabilityTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new NasException(NasException.Code.UNAVAILABLE);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new NasException(NasException.Code.UNAVAILABLE);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NasException n) {
                throw n;
            }
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
    }

    /** Live check of one share; throws UNAVAILABLE or NOT_CONFIGURED. @return free space in bytes */
    long requireAvailable(String shareName) {
        NasProperties.Share share = props.shares().get(shareName);
        if (share == null || share.mount() == null) {
            throw new NasException(NasException.Code.NOT_CONFIGURED);
        }
        return call(() -> check(share.mount()));
    }

    private long check(Path mount) throws IOException {
        if (!Files.isDirectory(mount) || !Files.isReadable(mount)) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
        if (!props.allowUnmountedDirectory() && !isMountPoint(mount)) {
            throw new NasException(NasException.Code.UNAVAILABLE);
        }
        return Files.getFileStore(mount).getUsableSpace();
    }

    /** A real mount point sits on a different file system than its parent directory. */
    static boolean isMountPoint(Path dir) throws IOException {
        Path real = dir.toRealPath();
        Path parent = real.getParent();
        if (parent == null) {
            return true;
        }
        FileStore here = Files.getFileStore(real);
        FileStore above = Files.getFileStore(parent);
        return !here.equals(above);
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 30_000)
    public void refresh() {
        props.shares().forEach((name, share) -> {
            try {
                long free = requireAvailable(name);
                cache.put(name, new ShareStatus(true, free, clock.instant()));
            } catch (NasException e) {
                cache.put(name, new ShareStatus(false, 0, clock.instant()));
            } catch (RuntimeException e) {
                log.warn("NAS check error for a share: {}", e.getClass().getSimpleName());
                cache.put(name, new ShareStatus(false, 0, clock.instant()));
            }
        });
    }

    /** Last known state; a share never checked yet is reported as not available. */
    public ShareStatus status(String shareName) {
        return cache.getOrDefault(shareName, new ShareStatus(false, 0, null));
    }

    public java.util.Set<String> shareNames() {
        return props.shares().keySet();
    }
}
