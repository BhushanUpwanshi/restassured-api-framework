package io.github.bhushanupwanshi.api.core.assertions;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import java.time.Duration;
import java.util.Map;
import org.testng.annotations.Test;

/** The assertion messages are the product here, so the tests pin the messages. */
public class ApiResponseAssertTest {

    private static final String VALID = """
            {"firstname":"Asha","lastname":"Rao","totalprice":111,"depositpaid":true,
             "bookingdates":{"checkin":"2026-11-01","checkout":"2026-11-05"},
             "additionalneeds":"Breakfast"}
            """;

    private static ApiResponse<Void> response(int status, long millis, String body) {
        return new ApiResponse<>(null, status, Map.of("content-type", "application/json"),
                Duration.ofMillis(millis), body);
    }

    @Test
    public void aPassingChainReadsAsOneStatement() {
        assertThatCode(() -> assertThat(response(200, 120, VALID))
                .hasStatus(200)
                .isSuccessful()
                .respondedWithin(Duration.ofMillis(800))
                .hasHeader("Content-Type", "application/json")
                .matchesSchema("schemas/booking.schema.json"))
                .doesNotThrowAnyException();
    }

    @Test
    public void aWrongStatusShowsWhatTheServerSaid() {
        assertThatThrownBy(() -> assertThat(response(500, 10, "Internal Server Error")).hasStatus(200))
                .hasMessageContaining("Expected HTTP 200 but got 500")
                .hasMessageContaining("Internal Server Error");
    }

    @Test
    public void aSlowResponseReportsBothTimes() {
        assertThatThrownBy(() -> assertThat(response(200, 950, VALID)).respondedWithin(Duration.ofMillis(800)))
                .hasMessageContaining("within 800 ms but it took 950 ms");
    }

    @Test
    public void aSchemaViolationNamesTheSchemaAndTheProblem() {
        String priceAsText = VALID.replace("111", "\"111\"");

        assertThatThrownBy(() -> assertThat(response(200, 10, priceAsText))
                .matchesSchema("schemas/booking.schema.json"))
                .hasMessageContaining("schemas/booking.schema.json")
                .hasMessageContaining("totalprice");
    }

    @Test
    public void aMissingHeaderListsTheHeadersThatWereThere() {
        assertThatThrownBy(() -> assertThat(response(200, 10, VALID)).hasHeader("X-Correlation-Id"))
                .hasMessageContaining("X-Correlation-Id")
                .hasMessageContaining("content-type");
    }

    @Test
    public void aLongBodyIsTruncatedInTheMessage() {
        String huge = "x".repeat(2_000);

        assertThatThrownBy(() -> assertThat(response(500, 10, huge)).hasStatus(200))
                .hasMessageContaining("(2000 chars)");
    }
}
