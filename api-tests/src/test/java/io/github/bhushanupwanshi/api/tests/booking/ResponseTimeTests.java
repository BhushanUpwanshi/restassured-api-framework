package io.github.bhushanupwanshi.api.tests.booking;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.data.BookingFactory;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import io.github.bhushanupwanshi.api.tests.support.RetryAnalyzer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Response time, throughput and error rate.
 *
 * <p>Not a load test: the target is a free shared host, and hammering it would be both
 * rude and meaningless. These are SLA checks, the kind that catch a ten-fold regression
 * on the way into a release. The budget defaults to 5 seconds because the host is a
 * sleeping dyno (observed 0.6 to 1.1 s warm); against a real service it would be set per
 * endpoint with {@code -Dsla.ms}.
 *
 * <p>Tagged {@value RetryAnalyzer#GROUP}, so a single slow run on shared infrastructure
 * gets one retry. Nothing functional is tagged that way.
 */
public class ResponseTimeTests extends BaseApiTest {

    private static final Logger log = LoggerFactory.getLogger(ResponseTimeTests.class);

    private static final Duration SLA = Duration.ofMillis(Long.getLong("sla.ms", 5_000));
    private static final int SAMPLES = 20;
    private static final int CONCURRENCY = 4;

    @Test(groups = {"regression", RetryAnalyzer.GROUP}, retryAnalyzer = RetryAnalyzer.class)
    public void everyBookingEndpointRespondsWithinTheSla() {
        var created = bookings().create(BookingFactory.valid());
        int id = created.requireBody().bookingid();
        cleanUpLater(id);

        assertThat(created).as("POST /booking").respondedWithin(SLA);
        assertThat(bookings().getById(id)).as("GET /booking/{id}").respondedWithin(SLA);
        assertThat(bookings().getAllIds()).as("GET /booking").respondedWithin(SLA);
        assertThat(bookings().update(id, BookingFactory.valid())).as("PUT /booking/{id}").respondedWithin(SLA);
    }

    @Test(groups = {"regression", RetryAnalyzer.GROUP}, retryAnalyzer = RetryAnalyzer.class)
    public void readsHoldTheirP95AndErrorRateUnderConcurrentLoad() throws Exception {
        int id = bookingFlow().createAndVerify(BookingFactory.valid()).id();

        List<ApiResponse<?>> responses = new ArrayList<>();
        long started = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<Future<ApiResponse<?>>> futures = new ArrayList<>();
            for (int i = 0; i < SAMPLES; i++) {
                futures.add(pool.submit(() -> bookings().getById(id)));
            }
            for (Future<ApiResponse<?>> future : futures) {
                responses.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }
        double seconds = (System.nanoTime() - started) / 1e9;

        long errors = responses.stream().filter(r -> !r.isSuccessful()).count();
        List<Duration> sorted = responses.stream().map(ApiResponse::duration).sorted(Comparator.naturalOrder()).toList();
        Duration p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
        double throughput = SAMPLES / seconds;

        log.info("{} reads at concurrency {}: p95={} ms, throughput={} req/s, errors={}",
                SAMPLES, CONCURRENCY, p95.toMillis(), "%.1f".formatted(throughput), errors);

        assertThat(errors).as("error rate over %d reads", SAMPLES).isZero();
        assertThat(p95).as("p95 latency").isLessThanOrEqualTo(SLA);
        assertThat(throughput).as("throughput, requests per second").isGreaterThan(1.0);
    }
}
