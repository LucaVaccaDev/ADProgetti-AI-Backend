package it.adprogetti.zio.edge.jobs;

import java.time.Duration;

/** Controlled retries: exponential backoff, capped, with a maximum number of attempts. */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double factor, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1 || initialBackoff == null || initialBackoff.isNegative() || factor < 1.0
                || maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("invalid retry policy");
        }
    }

    /** 3 attempts, waiting 30 s then 2 min. */
    public static RetryPolicy standard() {
        return new RetryPolicy(3, Duration.ofSeconds(30), 4.0, Duration.ofMinutes(10));
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    /** Wait before the attempt that follows {@code failedAttempts} failures (1 = after the first failure). */
    public Duration backoffAfter(int failedAttempts) {
        double millis = initialBackoff.toMillis() * Math.pow(factor, Math.max(0, failedAttempts - 1));
        long capped = (long) Math.min(millis, maxBackoff.toMillis());
        return Duration.ofMillis(capped);
    }
}
