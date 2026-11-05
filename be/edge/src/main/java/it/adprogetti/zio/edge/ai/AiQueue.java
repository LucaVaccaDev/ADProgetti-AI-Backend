package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.edge.config.EdgeProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Two lanes so the 24 GB machine is never overloaded (capitolato C.8): HEAVY (large models) runs one job at
 * a time, LIGHT (classification, extraction, embeddings) runs a few in parallel. Within a lane, higher
 * priority goes first, then arrival order.
 */
@Component
public class AiQueue {

    public enum Lane { LIGHT, HEAVY }

    private final ThreadPoolExecutor light;
    private final ThreadPoolExecutor heavy;
    private final AtomicLong sequence = new AtomicLong();

    AiQueue(EdgeProperties props) {
        this.light = pool(props.ai().lightConcurrency(), "ai-light");
        this.heavy = pool(props.ai().heavyConcurrency(), "ai-heavy");
    }

    private static ThreadPoolExecutor pool(int threads, String name) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, name);
                    t.setDaemon(true);
                    return t;
                });
    }

    public <T> CompletableFuture<T> submit(Lane lane, Priority priority, Callable<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Job job = new Job(priority, sequence.incrementAndGet(), () -> {
            try {
                future.complete(work.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        (lane == Lane.HEAVY ? heavy : light).execute(job);
        return future;
    }

    /** Submits and waits. Failures of the work are rethrown as they are. */
    public <T> T run(Lane lane, Priority priority, Callable<T> work) {
        try {
            return submit(lane, priority, work).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("INTERRUPTED", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    @PreDestroy
    void stop() {
        light.shutdownNow();
        heavy.shutdownNow();
    }

    private record Job(Priority priority, long seq, Runnable body) implements Runnable, Comparable<Job> {
        @Override
        public void run() {
            body.run();
        }

        @Override
        public int compareTo(Job other) {
            int byPriority = priority.compareTo(other.priority);
            return byPriority != 0 ? byPriority : Long.compare(seq, other.seq);
        }
    }
}
