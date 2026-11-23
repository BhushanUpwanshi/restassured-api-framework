package io.github.bhushanupwanshi.api.core.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.serialization.Json;
import org.testng.annotations.Test;

/** The typed response envelope. */
public class ApiResponseTest extends WireMockSupport {

    private static final String PATH = "/thing";

    /** Minimal body type for these tests. */
    public record Thing(String name, int size) {
    }

    private ApiResponse<Thing> fetch() {
        var specs = new Specs(configWithoutRetry(), false);
        var response = given().spec(specs.request()).get(PATH);
        return ApiResponse.from(response, raw -> Json.read(raw, Thing.class));
    }

    @Test
    public void bindsTheBodyToATypedObject() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\":\"widget\",\"size\":3}")));

        ApiResponse<Thing> response = fetch();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.requireBody().name()).isEqualTo("widget");
        assertThat(response.requireBody().size()).isEqualTo(3);
        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    public void ignoresFieldsTheModelDoesNotDeclare() {
        // Servers add fields. Failing on them would turn every server-side addition
        // into a suite-wide outage; schema tests catch contract drift deliberately.
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\":\"widget\",\"size\":3,\"addedLastTuesday\":true}")));

        assertThat(fetch().requireBody().name()).isEqualTo("widget");
    }

    @Test
    public void keepsTheRawBodyForErrorMessagesAndSchemaChecks() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\":\"widget\",\"size\":3}")));

        assertThat(fetch().rawBody()).contains("widget");
    }

    @Test
    public void leavesTheBodyNullWhenThereIsNone() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(204)));

        ApiResponse<Thing> response = fetch();

        assertThat(response.body()).isNull();
        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    public void requireBodyNamesTheStatusAndThePayloadWhenThereIsNoBody() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(401)));

        ApiResponse<Thing> response = fetch();

        // Without this the first symptom is a NullPointerException several lines later
        // with nothing about the 401 that caused it.
        assertThatThrownBy(response::requireBody)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("<empty>");
    }

    @Test
    public void reportsFailureForNon2xx() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/json").withBody("{}")));

        assertThat(fetch().isSuccessful()).isFalse();
    }

    @Test
    public void exposesHeadersCaseInsensitively() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Request-Trace", "abc123")
                .withBody("{\"name\":\"w\",\"size\":1}")));

        ApiResponse<Thing> response = fetch();

        assertThat(response.header("x-request-trace")).contains("abc123");
        assertThat(response.header("X-REQUEST-TRACE")).contains("abc123");
        assertThat(response.header("absent")).isEmpty();
    }

    @Test
    public void recordsAPlausibleDuration() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse()
                .withFixedDelay(300)
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\":\"w\",\"size\":1}")));

        // REST Assured's own measurement, not a stopwatch around the filter chain.
        // This is what makes a response-time assertion an ordinary assertion.
        assertThat(fetch().duration().toMillis()).isGreaterThanOrEqualTo(300);
    }
}
