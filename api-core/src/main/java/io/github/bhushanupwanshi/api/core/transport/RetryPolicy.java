package io.github.bhushanupwanshi.api.core.transport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;

/**
 * Decides whether a failed request is worth sending again, and how long to wait.
 *
 * <p>This is <strong>transport</strong> retry: resilience against infrastructure that
 * dropped a connection or answered 503 while a node restarted. It is not test retry.
 * Re-running a whole test hides product bugs, so that lives in TestNG's
 * {@code IRetryAnalyzer} and is scoped to the few tests that touch a third party.
 *
 * <p>Being able to say which of the two belongs where is the point. Conflating them is
 * how a suite becomes reliably green and tells you nothing.
 *
 * <p>Pure logic, no HTTP, so every rule below is unit-tested without a server.
 *
 * @param maxRetries attempts <em>after</em> the first; 0 disables retrying
 * @param backoff    base delay, doubled per attempt
 * @param maxBackoff ceiling on the delay, so a long backoff cannot outlast a timeout
 */
public record RetryPolicy(int maxRetries, Duration backoff, Duration maxBackoff) {

    /**
     * Status codes worth another attempt.
     *
     * <p>Only codes that mean "this might work shortly". A 500 is excluded on purpose:
     * it usually means the request itself provoked a bug, and retrying it turns one
     * deterministic failure into three identical ones and a slower suite. 429 is
     * included because the server is explicitly asking for a pause.
     */
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);

    public RetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        if (backoff.isNegative() || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("backoff durations must not be negative");
        }
        if (maxBackoff.compareTo(backoff) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff (%s) must not be shorter than backoff (%s)".formatted(maxBackoff, backoff));
        }
    }

    /** A policy that never retries. */
    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, Duration.ZERO);
    }

    /** A policy with a ten-fold ceiling on the base delay. */
    public static RetryPolicy of(int maxRetries, Duration backoff) {
        return new RetryPolicy(maxRetries, backoff, backoff.multipliedBy(10));
    }

    /** Whether a response status should be retried. */
    public boolean shouldRetry(int statusCode) {
        return maxRetries > 0 && RETRYABLE_STATUSES.contains(statusCode);
    }

    /**
     * Whether a thrown exception should be retried.
     *
     * <p>Connection-level faults and read timeouts, nothing else. A malformed URI or a
     * serialization failure is a defect in the test, and retrying it only delays the
     * report.
     */
    public boolean shouldRetry(Throwable error) {
        if (maxRetries == 0) {
            return false;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof InterruptedIOException
                    || isConnectionReset(cause)) {
                return true;
            }
            if (cause == cause.getCause()) {
                break; // self-referencing cause chain; stop rather than loop forever
            }
        }
        return false;
    }

    /**
     * How long to wait before a given attempt.
     *
     * @param attempt 1 for the wait before the first retry, 2 for the next, and so on
     * @return the delay, never longer than {@link #maxBackoff}
     */
    public Duration delayBeforeAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be 1 or greater: " + attempt);
        }
        // Doubling via multipliedBy on a long is safe for any attempt count a test
        // would use; the ceiling applies well before overflow is conceivable.
        long multiplier = 1L << Math.min(attempt - 1, 32);
        Duration grown = backoff.multipliedBy(multiplier);
        return grown.compareTo(maxBackoff) > 0 ? maxBackoff : grown;
    }

    /** Total attempts this policy permits, including the first. */
    public int maxAttempts() {
        return maxRetries + 1;
    }

    private static boolean isConnectionReset(Throwable cause) {
        // "Connection reset" arrives as a plain SocketException or IOException with the
        // reason only in the message, so there is no type to match on.
        if (!(cause instanceof SocketException || cause instanceof IOException)) {
            return false;
        }
        String message = cause.getMessage();
        return message != null && message.toLowerCase().contains("connection reset");
    }
}
