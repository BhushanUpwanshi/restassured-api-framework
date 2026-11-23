package io.github.bhushanupwanshi.api.core.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.testng.annotations.Test;

/**
 * The retry rules, with no HTTP involved.
 *
 * <p>Separating the decisions from the loop is what makes this possible: every rule the
 * transport layer follows is asserted here in milliseconds, and {@link RetryFilterTest}
 * then only has to show the loop obeys them.
 */
public class RetryPolicyTest {

    private static final RetryPolicy POLICY = RetryPolicy.of(2, Duration.ofMillis(100));

    @Test
    public void retriesStatusesThatMeanTryAgainShortly() {
        assertThat(POLICY.shouldRetry(429)).isTrue();
        assertThat(POLICY.shouldRetry(502)).isTrue();
        assertThat(POLICY.shouldRetry(503)).isTrue();
        assertThat(POLICY.shouldRetry(504)).isTrue();
    }

    /**
     * 500 is excluded on purpose.
     *
     * <p>It usually means the request provoked a bug, so retrying turns one
     * deterministic failure into three identical ones and a slower suite.
     */
    @Test
    public void doesNotRetryAnInternalServerError() {
        assertThat(POLICY.shouldRetry(500)).isFalse();
    }

    @Test
    public void doesNotRetrySuccessOrClientErrors() {
        assertThat(POLICY.shouldRetry(200)).isFalse();
        assertThat(POLICY.shouldRetry(201)).isFalse();
        assertThat(POLICY.shouldRetry(400)).isFalse();
        assertThat(POLICY.shouldRetry(401)).isFalse();
        assertThat(POLICY.shouldRetry(404)).isFalse();
        assertThat(POLICY.shouldRetry(422)).isFalse();
    }

    @Test
    public void retriesConnectionLevelFaults() {
        assertThat(POLICY.shouldRetry(new SocketTimeoutException("read timed out"))).isTrue();
        assertThat(POLICY.shouldRetry(new ConnectException("refused"))).isTrue();
        assertThat(POLICY.shouldRetry(new UnknownHostException("dns"))).isTrue();
        assertThat(POLICY.shouldRetry(new SocketException("Connection reset"))).isTrue();
    }

    @Test
    public void findsARetryableCauseInsideAWrapper() {
        // REST Assured and HttpClient wrap the real fault several layers deep, so
        // matching only the outermost exception would never retry anything.
        Exception wrapped = new IllegalStateException("request failed",
                new RuntimeException("transport", new SocketTimeoutException("read timed out")));

        assertThat(POLICY.shouldRetry(wrapped)).isTrue();
    }

    @Test
    public void doesNotRetryAFaultInTheTest() {
        assertThat(POLICY.shouldRetry(new IllegalArgumentException("bad URI"))).isFalse();
        assertThat(POLICY.shouldRetry(new NullPointerException())).isFalse();
        assertThat(POLICY.shouldRetry(new IOException("broken pipe"))).isFalse();
    }

    /**
     * A cause chain that points at itself must not spin forever.
     *
     * <p>{@code initCause(this)} cannot build one: the JDK rejects it with
     * "Self-causation not permitted". A type that overrides {@code getCause} can, and
     * libraries that synthesise exceptions do occasionally produce chains like it, which
     * is why the guard is there rather than assumed unnecessary.
     */
    @Test
    public void survivesASelfReferencingCauseChain() {
        class SelfCausing extends RuntimeException {
            SelfCausing() {
                super("points at itself");
            }

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        }

        assertThat(POLICY.shouldRetry(new SelfCausing())).isFalse();
    }

    @Test
    public void aPolicyWithNoBudgetRetriesNothing() {
        RetryPolicy none = RetryPolicy.none();

        assertThat(none.shouldRetry(503)).isFalse();
        assertThat(none.shouldRetry(new SocketTimeoutException())).isFalse();
        assertThat(none.maxAttempts()).isEqualTo(1);
    }

    @Test
    public void backoffDoublesPerAttempt() {
        assertThat(POLICY.delayBeforeAttempt(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(POLICY.delayBeforeAttempt(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(POLICY.delayBeforeAttempt(3)).isEqualTo(Duration.ofMillis(400));
    }

    @Test
    public void backoffStopsAtTheCeiling() {
        // Without a ceiling, doubling outlasts the read timeout and the suite appears
        // to hang rather than fail.
        RetryPolicy capped = new RetryPolicy(10, Duration.ofMillis(100), Duration.ofMillis(300));

        assertThat(capped.delayBeforeAttempt(3)).isEqualTo(Duration.ofMillis(300));
        assertThat(capped.delayBeforeAttempt(9)).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    public void maxAttemptsIncludesTheFirstTry() {
        assertThat(RetryPolicy.of(0, Duration.ZERO).maxAttempts()).isEqualTo(1);
        assertThat(RetryPolicy.of(2, Duration.ofMillis(1)).maxAttempts()).isEqualTo(3);
    }

    @Test
    public void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(-1, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries");

        assertThatThrownBy(() ->
                        new RetryPolicy(2, Duration.ofMillis(500), Duration.ofMillis(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBackoff");

        assertThatThrownBy(() -> POLICY.delayBeforeAttempt(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
