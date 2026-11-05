package it.adprogetti.zio.edge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.edge.config.EdgeProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiQueueTest {

    private AiQueue queue(int light, int heavy) {
        return new AiQueue(new EdgeProperties("t", null, null,
                new EdgeProperties.Ai("", "test", light, heavy, null, null)));
    }

    @Test
    void theHeavyLaneRunsOneJobAtATime() throws Exception {
        AiQueue q = queue(2, 1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<CompletableFuture<Integer>> all = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(q.submit(AiQueue.Lane.HEAVY, Priority.NORMAL, () -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                Thread.sleep(80);
                running.decrementAndGet();
                return 1;
            }));
        }
        for (CompletableFuture<Integer> f : all) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    void theLightLaneRunsInParallel() throws Exception {
        AiQueue q = queue(2, 1);
        CountDownLatch bothRunning = new CountDownLatch(2);
        CompletableFuture<Boolean> a = q.submit(AiQueue.Lane.LIGHT, Priority.NORMAL, () -> {
            bothRunning.countDown();
            return bothRunning.await(3, TimeUnit.SECONDS);
        });
        CompletableFuture<Boolean> b = q.submit(AiQueue.Lane.LIGHT, Priority.NORMAL, () -> {
            bothRunning.countDown();
            return bothRunning.await(3, TimeUnit.SECONDS);
        });
        assertThat(a.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void waitingJobsRunByPriorityThenArrival() throws Exception {
        AiQueue q = queue(1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> blocker = q.submit(AiQueue.Lane.HEAVY, Priority.NORMAL, () -> {
            started.countDown();
            release.await();
            return null;
        });
        started.await(3, TimeUnit.SECONDS);
        CompletableFuture<?> low = q.submit(AiQueue.Lane.HEAVY, Priority.LOW, () -> order.add("low"));
        CompletableFuture<?> normal1 = q.submit(AiQueue.Lane.HEAVY, Priority.NORMAL, () -> order.add("normal1"));
        CompletableFuture<?> high = q.submit(AiQueue.Lane.HEAVY, Priority.HIGH, () -> order.add("high"));
        CompletableFuture<?> normal2 = q.submit(AiQueue.Lane.HEAVY, Priority.NORMAL, () -> order.add("normal2"));
        release.countDown();
        for (CompletableFuture<?> f : List.of(blocker, low, normal1, high, normal2)) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertThat(order).containsExactly("high", "normal1", "normal2", "low");
    }

    @Test
    void aFailingJobDoesNotStopTheLane() throws Exception {
        AiQueue q = queue(1, 1);
        CompletableFuture<Object> bad = q.submit(AiQueue.Lane.LIGHT, Priority.NORMAL, () -> {
            throw new IllegalStateException("boom");
        });
        CompletableFuture<String> good = q.submit(AiQueue.Lane.LIGHT, Priority.NORMAL, () -> "fine");
        assertThat(good.get(3, TimeUnit.SECONDS)).isEqualTo("fine");
        assertThat(bad.isCompletedExceptionally()).isTrue();
    }
}
