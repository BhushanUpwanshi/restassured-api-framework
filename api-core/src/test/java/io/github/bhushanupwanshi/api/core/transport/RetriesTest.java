package io.github.bhushanupwanshi.api.core.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.restassured.response.Response;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.testng.annotations.Test;

/**
 * Proves the retry loop obeys {@link RetryPolicy} over real HTTP.
 *
 * <p>The rules are covered in {@link RetryPolicyTest} without a server. What is in
 * question here is whether the request is genuinely re-sent, so the request counts
 * recorded by the stub are the assertions that matter.
 *
 * <p>This suite is also what established that a filter cannot do this job: an earlier
 * {@code RetryFilter} calling {@code ctx.next()} a second time got {@code null} back,
 * because the filter chain is a single-use iterator. See {@link Retries}.
 */
public class RetriesTest extends WireMockSupport {

    private static final String PATH = "/flaky";

    /** One attempt: a complete request with its own filter chain. */
    private Response send(Specs specs) {
        return given().spec(specs.request()).get(PATH);
    }

    private Response executeWith(int maxRetries) {
        Specs specs = new Specs(config(maxRetries, Duration.ofMillis(20)), false);
        return Retries.execute(specs.retryPolicy(), "GET " + PATH, () -> send(specs));
    }

    @Test
    public void retriesA503AndSucceedsOnTheSecondAttempt() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        assertThat(executeWith(2).getStatusCode()).isEqualTo(200);
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void givesUpAfterTheBudgetAndReturnsTheLastResponse() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        // Returned, not thrown: an assertion downstream then reports the real 503
        // instead of a framework error that hides it.
        assertThat(executeWith(2).getStatusCode()).isEqualTo(503);
        wireMock.verify(3, getRequestedFor(urlEqualTo(PATH))); // 1 try + 2 retries
    }

    @Test
    public void doesNotRetryAClientError() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThat(executeWith(2).getStatusCode()).isEqualTo(400);
        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void doesNotRetryAnInternalServerError() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(executeWith(2).getStatusCode()).isEqualTo(500);
        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void sendsOnceWhenTheFirstAttemptSucceeds() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThat(executeWith(2).getStatusCode()).isEqualTo(200);
        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void sendsOnceWhenRetryingIsDisabled() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThat(executeWith(0).getStatusCode()).isEqualTo(503);
        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void retriesAReadTimeout() {
        // The stub holds the connection past the 2s read timeout in the test config,
        // which surfaces as a thrown exception rather than a status code.
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("slow")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withFixedDelay(3_000).withStatus(200))
                .willSetStateTo("fast"));
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("slow")
                .whenScenarioStateIs("fast")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThat(executeWith(2).getStatusCode()).isEqualTo(200);
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void rethrowsWhenEveryAttemptTimesOut() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse().withFixedDelay(3_000).withStatus(200)));

        // The original transport error propagates, unwrapped. SocketTimeoutException
        // is checked, which is why the loop catches Throwable rather than
        // RuntimeException; asserting the exact type is what pins that down.
        assertThatThrownBy(() -> executeWith(1)).isInstanceOf(SocketTimeoutException.class);
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void eachAttemptCarriesItsOwnCorrelationId() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("ids")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("ids")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        executeWith(2);

        var ids = wireMock.findAll(getRequestedFor(urlEqualTo(PATH))).stream()
                .map(request -> request.getHeader(CorrelationIdFilter.HEADER))
                .distinct()
                .toList();

        // Two requests reached the server, so its log holds two entries. One id shared
        // between them would make the second unfindable, which is the reason retrying
        // at this level beats re-entering a filter chain.
        assertThat(ids).hasSize(2);
    }
}
