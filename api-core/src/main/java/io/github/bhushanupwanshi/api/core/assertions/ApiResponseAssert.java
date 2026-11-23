package io.github.bhushanupwanshi.api.core.assertions;

import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.restassured.module.jsv.JsonSchemaValidator;
import java.time.Duration;
import org.assertj.core.api.AbstractAssert;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

/**
 * Assertions on a whole response: status, timing, headers and contract in one chain.
 *
 * <pre>{@code
 * assertThat(response)
 *         .hasStatus(200)
 *         .respondedWithin(Duration.ofMillis(800))
 *         .matchesSchema("schemas/booking.schema.json");
 * }</pre>
 *
 * <p>Every failure message carries the status and the raw body. A bare
 * "expected 200 but was 500" sends you to the CI log to find out what the server said;
 * the body usually says it.
 */
public class ApiResponseAssert extends AbstractAssert<ApiResponseAssert, ApiResponse<?>> {

    public ApiResponseAssert(ApiResponse<?> actual) {
        super(actual, ApiResponseAssert.class);
    }

    /** The status is exactly {@code expected}. */
    public ApiResponseAssert hasStatus(int expected) {
        isNotNull();
        if (actual.statusCode() != expected) {
            failWithMessage("Expected HTTP %d but got %d.%nBody: %s",
                    expected, actual.statusCode(), shortBody());
        }
        return this;
    }

    /** The status is 2xx. */
    public ApiResponseAssert isSuccessful() {
        isNotNull();
        if (!actual.isSuccessful()) {
            failWithMessage("Expected a 2xx status but got %d.%nBody: %s",
                    actual.statusCode(), shortBody());
        }
        return this;
    }

    /**
     * The round trip took no longer than {@code limit}.
     *
     * <p>Measured by REST Assured on the attempt that produced the response; see
     * {@link ApiResponse#duration()}.
     */
    public ApiResponseAssert respondedWithin(Duration limit) {
        isNotNull();
        if (actual.duration().compareTo(limit) > 0) {
            failWithMessage("Expected a response within %d ms but it took %d ms (HTTP %d)",
                    limit.toMillis(), actual.duration().toMillis(), actual.statusCode());
        }
        return this;
    }

    /**
     * The raw body validates against a JSON schema on the classpath.
     *
     * <p>Validates the raw body, not the bound object. Binding ignores unknown fields on
     * purpose, so a field the server silently added or renamed would never show up in the
     * typed model; the schema is where that drift gets caught.
     *
     * @param classpathSchema e.g. {@code schemas/booking.schema.json}
     */
    public ApiResponseAssert matchesSchema(String classpathSchema) {
        isNotNull();
        Matcher<?> matcher = JsonSchemaValidator.matchesJsonSchemaInClasspath(classpathSchema);
        if (!matcher.matches(actual.rawBody())) {
            StringDescription mismatch = new StringDescription();
            matcher.describeMismatch(actual.rawBody(), mismatch);
            failWithMessage("Body does not match schema %s:%n%s%nBody: %s",
                    classpathSchema, mismatch, shortBody());
        }
        return this;
    }

    /** A header is present, in any letter case. */
    public ApiResponseAssert hasHeader(String name) {
        isNotNull();
        if (actual.header(name).isEmpty()) {
            failWithMessage("Expected header \"%s\" but the response had: %s",
                    name, actual.headers().keySet());
        }
        return this;
    }

    /** A header is present with exactly this value. */
    public ApiResponseAssert hasHeader(String name, String value) {
        hasHeader(name);
        String found = actual.header(name).orElseThrow();
        if (!found.equals(value)) {
            failWithMessage("Expected header \"%s\" to be \"%s\" but was \"%s\"", name, value, found);
        }
        return this;
    }

    /** The raw body contains {@code text}. For this API's plain-text error bodies. */
    public ApiResponseAssert hasBodyContaining(String text) {
        isNotNull();
        if (!actual.rawBody().contains(text)) {
            failWithMessage("Expected the body to contain \"%s\" but it was: %s", text, shortBody());
        }
        return this;
    }

    private String shortBody() {
        String raw = actual.rawBody();
        if (raw == null || raw.isBlank()) {
            return "<empty>";
        }
        return raw.length() > 500 ? raw.substring(0, 500) + "... (" + raw.length() + " chars)" : raw;
    }
}
