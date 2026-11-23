package io.github.bhushanupwanshi.api.core.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.restassured.response.Response;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.testng.annotations.Test;

/** What the shared specification actually applies to a request. */
public class SpecsTest extends WireMockSupport {

    private static final String PATH = "/things";

    private Specs specs() {
        return new Specs(configWithoutRetry(), false);
    }

    @Test
    public void appliesTheBaseUriSoCallsUsePathsOnly() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        given().spec(specs().request()).get(PATH).then().statusCode(200);

        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void sendsAndAcceptsJson() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        given().spec(specs().request()).get(PATH);

        var request = wireMock.findAll(getRequestedFor(urlEqualTo(PATH))).get(0);
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getHeader("Accept")).contains("application/json");
    }

    @Test
    public void stampsEveryRequestWithACorrelationId() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        given().spec(specs().request()).get(PATH);

        var request = wireMock.findAll(getRequestedFor(urlEqualTo(PATH))).get(0);
        assertThat(request.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(CorrelationIdFilter.lastId()).isEqualTo(request.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test
    public void givesEveryRequestADifferentCorrelationId() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        given().spec(specs().request()).get(PATH);
        given().spec(specs().request()).get(PATH);

        var ids = wireMock.findAll(getRequestedFor(urlEqualTo(PATH))).stream()
                .map(request -> request.getHeader(CorrelationIdFilter.HEADER))
                .distinct()
                .toList();
        assertThat(ids).hasSize(2);
    }

    @Test
    public void appliesTheReadTimeout() {
        // 2s read timeout in the test config against a 4s stub. Without the timeout
        // actually reaching HttpClient this would wait the full four seconds and pass.
        wireMock.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse().withFixedDelay(4_000).withStatus(200)));

        long started = System.currentTimeMillis();
        // REST Assured lets the checked SocketTimeoutException escape as-is.
        assertThatThrownBy(() -> given().spec(specs().request()).get(PATH))
                .isInstanceOf(SocketTimeoutException.class);

        assertThat(System.currentTimeMillis() - started).isLessThan(3_500);
    }

    @Test
    public void derivesTheRetryPolicyFromTheConfig() {
        Specs specs = new Specs(config(3, Duration.ofMillis(250)), false);

        assertThat(specs.retryPolicy().maxRetries()).isEqualTo(3);
        assertThat(specs.retryPolicy().backoff()).isEqualTo(Duration.ofMillis(250));
        assertThat(specs.retryPolicy().maxAttempts()).isEqualTo(4);
    }

    @Test
    public void successfulJsonSpecAcceptsAny2xx() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{}")));

        given().spec(specs().request()).get(PATH).then().spec(specs().successfulJson());
    }

    @Test
    public void successfulJsonSpecRejectsA4xx() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(404).withHeader("Content-Type", "application/json").withBody("{}")));

        Response response = given().spec(specs().request()).get(PATH);

        assertThatThrownBy(() -> response.then().spec(specs().successfulJson()))
                .isInstanceOf(AssertionError.class);
    }
}
