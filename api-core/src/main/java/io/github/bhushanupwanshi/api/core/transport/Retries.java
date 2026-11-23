package io.github.bhushanupwanshi.api.core.transport;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a request, re-sending it when {@link RetryPolicy} says the failure was
 * transport-level.
 *
 * <p><strong>Why this is not a REST Assured filter.</strong> The obvious design is a
 * {@code Filter} that calls {@code ctx.next()} again on a retryable status, and it does
 * not work: {@code FilterContextImpl.next()} walks an iterator over the remaining
 * filters, so the second call finds the chain exhausted and returns {@code null}. The
 * attempt fails with a {@link NullPointerException} inside the filter rather than
 * retrying. Only an HTTP-level test shows this; the signature suggests it is fine.
 *
 * <p>Retrying at this level is better anyway. Each attempt is a complete request with a
 * fresh filter chain, so it gets its own correlation id and its own Allure attachment,
 * which is what a server-side log will show. A filter re-entering its own chain would
 * have reported one id for several requests.
 *
 * <p>The consequence worth knowing: retry applies to calls made through the client
 * layer, not to a bare {@code given().spec(...)} in a test. That is the right boundary,
 * since retry is a policy about how the framework talks to a service rather than a
 * property of a specification.
 */
public final class Retries {

    private static final Logger log = LoggerFactory.getLogger(Retries.class);

    private Retries() {
    }

    /**
     * Sends a request, retrying per the policy.
     *
     * @param policy      what to retry and how long to wait
     * @param description what is being called, for log lines
     * @param send        performs one attempt; called again for each retry
     * @return the last response, retryable status included once the budget is spent
     * @throws RuntimeException the last error, when every attempt threw
     */
    public static Response execute(RetryPolicy policy, String description, Supplier<Response> send) {
        Throwable lastError = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                Response response = send.get();

                boolean lastAttempt = attempt == policy.maxAttempts();
                if (!policy.shouldRetry(response.getStatusCode()) || lastAttempt) {
                    if (attempt > 1) {
                        log.info("{} finished on attempt {} of {} with status {}",
                                description, attempt, policy.maxAttempts(), response.getStatusCode());
                    }
                    // The last real response is returned rather than thrown, so the
                    // assertion that follows reports the status the server gave. A
                    // thrown exception here would hide a genuine 503 behind a
                    // framework error.
                    return response;
                }

                log.warn("{} returned {}; retrying (attempt {} of {})",
                        description, response.getStatusCode(), attempt, policy.maxAttempts());

            } catch (Throwable e) {
                // Throwable, not RuntimeException. REST Assured lets the raw
                // SocketTimeoutException escape, and that is a *checked* exception:
                // catching RuntimeException missed read timeouts entirely, which is
                // the single case this loop most needs to handle. Nothing but an
                // HTTP-level test surfaces that, because the compiler is satisfied
                // either way.
                if (!policy.shouldRetry(e) || attempt == policy.maxAttempts()) {
                    throw rethrow(e);
                }
                lastError = e;
                log.warn("{} failed with {}; retrying (attempt {} of {})",
                        description, e.getClass().getSimpleName(), attempt, policy.maxAttempts());
            }

            sleep(policy.delayBeforeAttempt(attempt));
        }

        throw lastError != null
                ? rethrow(lastError)
                : new IllegalStateException("Retry loop ended without a response or an error");
    }

    /**
     * Rethrows the original error unchanged, checked or not.
     *
     * <p>Wrapping it in a {@code RuntimeException} would be the conventional move and is
     * worse here: the transport error is the diagnosis. A test that times out should
     * report {@code SocketTimeoutException: Read timed out}, not a framework wrapper the
     * reader has to unpack to reach the same sentence.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException rethrow(Throwable error) throws E {
        throw (E) error;
    }

    private static void sleep(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            // Restore the flag rather than swallowing it: a suite being shut down
            // should stop, not finish its backoff and carry on.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}
