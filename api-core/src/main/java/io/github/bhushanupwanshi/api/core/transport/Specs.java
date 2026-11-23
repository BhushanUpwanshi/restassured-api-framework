package io.github.bhushanupwanshi.api.core.transport;

import io.github.bhushanupwanshi.api.core.config.FrameworkConfig;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.time.Duration;
import org.hamcrest.Matchers;

/**
 * Builds the request and response specifications every call shares.
 *
 * <p>Built once per config rather than per test. A test should state what is
 * <em>different</em> about its call, not repeat the base URI, the content type, the
 * timeouts and the filters that are the same for all of them. That repetition is not
 * only noise: it drifts, and then one test has a timeout the others do not for a reason
 * nobody remembers.
 *
 * <p>Retrying is deliberately <em>not</em> here. It lives in {@link Retries}, because a
 * REST Assured filter cannot re-send a request: {@code ctx.next()} is single-use and
 * returns {@code null} on a second call. See {@link Retries} for the detail.
 *
 * <p>Filters, outermost first:
 *
 * <ol>
 *   <li>{@link CorrelationIdFilter} stamps the request with a traceable id
 *   <li>{@code AllureRestAssured} attaches the full request and response to the report
 *   <li>{@link HttpLogFilter} logs one scannable line
 * </ol>
 */
public final class Specs {

    private final FrameworkConfig config;
    private final RetryPolicy retryPolicy;
    private final boolean reportToAllure;

    /** Specs that attach every call to the Allure report. */
    public Specs(FrameworkConfig config) {
        this(config, true);
    }

    /**
     * @param config         resolved settings
     * @param reportToAllure whether to attach requests to Allure. The framework's own
     *     tests pass {@code false}: they run without an Allure lifecycle, and the filter
     *     logs "Could not add attachment: no test is running" for every single call,
     *     which buries the output that matters.
     */
    public Specs(FrameworkConfig config, boolean reportToAllure) {
        this.config = config;
        this.reportToAllure = reportToAllure;
        this.retryPolicy = RetryPolicy.of(config.maxRetries(), config.retryBackoff());
    }

    /** The shared request specification: base URI, JSON, timeouts and filters. */
    public RequestSpecification request() {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(config.baseUri())
                .setContentType(ContentType.JSON)
                // Exactly "application/json", not ContentType.JSON. That enum sends
                // "application/json, application/javascript, text/javascript, text/json",
                // and restful-booker answers anything but an exact match with
                // 418 I'm a Teapot. Found on the first live run, 2026-11-23: every stub
                // test passed because WireMock does not care about Accept.
                .setAccept("application/json")
                .setConfig(httpConfig(config.connectTimeout(), config.readTimeout()))
                .addFilter(new CorrelationIdFilter());

        if (reportToAllure) {
            builder.addFilter(new AllureRestAssured());
        }

        return builder.addFilter(new HttpLogFilter()).build();
    }

    /** Expects any 2xx and a JSON body. */
    public ResponseSpecification successfulJson() {
        return new ResponseSpecBuilder()
                .expectStatusCode(Matchers.allOf(
                        Matchers.greaterThanOrEqualTo(200), Matchers.lessThan(300)))
                .expectContentType(ContentType.JSON)
                .build();
    }

    /** The retry policy derived from the config, for the client layer to apply. */
    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** The config these specs were built from. */
    public FrameworkConfig config() {
        return config;
    }

    /**
     * Timeouts, set on the underlying Apache HttpClient.
     *
     * <p>REST Assured has no timeout setting of its own; these are HttpClient parameter
     * names, which is why they are strings rather than typed methods.
     */
    private static RestAssuredConfig httpConfig(Duration connectTimeout, Duration readTimeout) {
        return RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", (int) connectTimeout.toMillis())
                        .setParam("http.socket.timeout", (int) readTimeout.toMillis()))
                // Without this REST Assured serialises outbound bodies with its own
                // default Jackson instance, not the configured one in Json. The symptom
                // is specific and quiet: a LocalDate goes out as [2026,11,1] instead of
                // "2026-11-01", and the server rejects a payload that looks correct in
                // the test. One mapper for both directions, or there are effectively two
                // serialisation policies and only one of them was designed.
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .jackson2ObjectMapperFactory((type, charset) -> Json.mapper()));
    }
}
