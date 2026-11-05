package it.adprogetti.zio.edge.support;

import it.adprogetti.zio.edge.jobs.JobContext;
import it.adprogetti.zio.edge.jobs.JobFailure;
import it.adprogetti.zio.edge.jobs.JobHandler;
import it.adprogetti.zio.edge.jobs.RetryPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Scripted job handlers for engine tests. They live in the shared test context (one context, one port). */
@TestConfiguration
public class TestJobHandlers {

    /** How many times each job id was executed (the engine must never run a job twice at once or twice in total). */
    public static final Map<UUID, AtomicInteger> RUNS = new ConcurrentHashMap<>();
    public static final AtomicInteger CONCURRENT = new AtomicInteger();
    public static final AtomicInteger MAX_CONCURRENT = new AtomicInteger();
    public static volatile CountDownLatch release = new CountDownLatch(1);
    public static volatile CountDownLatch started = new CountDownLatch(1);

    private static final RetryPolicy FAST = new RetryPolicy(5, Duration.ofMillis(20), 1.0, Duration.ofMillis(20));

    private static void count(JobContext c) {
        RUNS.computeIfAbsent(c.id(), k -> new AtomicInteger()).incrementAndGet();
    }

    private static JobHandler handler(String type, RetryPolicy retry, boolean cancellable, boolean safe, Body body) {
        return new JobHandler() {
            public String type() { return type; }
            public String run(JobContext c) throws Exception { count(c); return body.run(c); }
            public RetryPolicy retryPolicy() { return retry; }
            public boolean cancellable() { return cancellable; }
            public boolean restartSafe() { return safe; }
        };
    }

    @FunctionalInterface
    interface Body {
        String run(JobContext c) throws Exception;
    }

    @Bean JobHandler testOk() {
        return handler("TEST_OK", FAST, false, true, c -> {
            Thread.sleep(5);
            return "{\"ok\":true}";
        });
    }

    /** Fails (retryable) until the attempt number given in the payload, e.g. {"succeedAt":3}. */
    @Bean JobHandler testFlaky() {
        return handler("TEST_FLAKY", FAST, false, true, c -> {
            int succeedAt = Integer.parseInt(c.payloadJson().replaceAll("\\D", ""));
            if (c.attempt() < succeedAt) {
                throw JobFailure.transientFailure("TEMPORARY");
            }
            return null;
        });
    }

    @Bean JobHandler testPermanent() {
        return handler("TEST_PERMANENT", FAST, false, true, c -> {
            throw JobFailure.permanent("BAD_INPUT");
        });
    }

    @Bean JobHandler testAlwaysFails() {
        return handler("TEST_ALWAYS_FAILS", new RetryPolicy(3, Duration.ofMillis(10), 1.0, Duration.ofMillis(10)),
                false, true, c -> {
                    throw new IllegalStateException("boom with a secret that must not be stored");
                });
    }

    /** Records how many jobs ran at once: with a resource key, the maximum must be 1. */
    @Bean JobHandler testExclusive() {
        return handler("TEST_EXCLUSIVE", FAST, false, true, c -> {
            int now = CONCURRENT.incrementAndGet();
            MAX_CONCURRENT.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(80);
            } finally {
                CONCURRENT.decrementAndGet();
            }
            return null;
        });
    }

    @Bean JobHandler testCancellable() {
        return handler("TEST_CANCELLABLE", FAST, true, true, c -> {
            started.countDown();
            while (true) {
                c.checkpoint();
                Thread.sleep(20);
            }
        });
    }

    @Bean JobHandler testBlocking() {
        return handler("TEST_BLOCKING", FAST, false, true, c -> {
            started.countDown();
            release.await();
            return null;
        });
    }

    @Bean JobHandler testUnsafe() {
        return handler("TEST_UNSAFE", FAST, false, false, c -> null);
    }

    @Bean JobHandler testScheduled() {
        return handler("TEST_SCHEDULED", FAST, false, true, c -> null);
    }

    public static void reset() {
        release = new CountDownLatch(1);
        started = new CountDownLatch(1);
        CONCURRENT.set(0);
        MAX_CONCURRENT.set(0);
    }
}
