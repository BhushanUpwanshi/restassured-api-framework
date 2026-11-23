package io.github.bhushanupwanshi.api.core.models;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.models.error.ApiError;
import org.testng.annotations.Test;

/**
 * Error normalisation across the shapes the target API actually returns.
 *
 * <p>Every case below is a real observed response, not a hypothetical. The point of the
 * class is that parsing an error must not itself fail: a model expecting JSON would throw
 * on {@code Not Found} and replace a clear 404 with a deserialization stack trace.
 */
public class ApiErrorTest {

    @Test
    public void readsTheReasonFieldFromAJsonError() {
        ApiError error = ApiError.from(200, "{\"reason\":\"Bad credentials\"}");

        assertThat(error.reason()).isEqualTo("Bad credentials");
        assertThat(error.statusCode()).isEqualTo(200);
    }

    @Test
    public void usesPlainTextAsTheReason() {
        // Observed: 404 returns exactly this, with no JSON.
        assertThat(ApiError.from(404, "Not Found").reason()).isEqualTo("Not Found");
        assertThat(ApiError.from(403, "Forbidden").reason()).isEqualTo("Forbidden");
    }

    @Test
    public void survivesAnEmptyBody() {
        ApiError error = ApiError.from(500, "");

        assertThat(error.reason()).contains("500").contains("empty body");
    }

    @Test
    public void survivesANullBody() {
        assertThat(ApiError.from(502, null).reason()).contains("502");
    }

    @Test
    public void survivesMalformedJson() {
        ApiError error = ApiError.from(500, "{\"reason\": ");

        // Falls back to the raw text rather than throwing, so the real 500 survives.
        assertThat(error.reason()).contains("reason");
        assertThat(error.rawBody()).isEqualTo("{\"reason\": ");
    }

    @Test
    public void fallsBackToTheBodyWhenJsonHasNoReasonField() {
        ApiError error = ApiError.from(422, "{\"message\":\"nope\"}");

        assertThat(error.reason()).contains("message");
    }

    @Test
    public void treatsANullReasonFieldAsAbsent() {
        ApiError error = ApiError.from(400, "{\"reason\":null}");

        assertThat(error.reason()).contains("reason");
    }

    @Test
    public void keepsTheBodyVerbatim() {
        String body = "  Not Found  ";

        assertThat(ApiError.from(404, body).rawBody()).isEqualTo(body);
        assertThat(ApiError.from(404, body).reason()).isEqualTo("Not Found");
    }

    /**
     * 403 counts as an auth failure, not only 401.
     *
     * <p>restful-booker answers a missing or stale token with 403. Checking 401 alone,
     * which is what the textbook says, would never trigger a token refresh here.
     */
    @Test
    public void recognisesAuthFailures() {
        assertThat(ApiError.from(401, "").isAuthFailure()).isTrue();
        assertThat(ApiError.from(403, "Forbidden").isAuthFailure()).isTrue();

        assertThat(ApiError.from(200, "").isAuthFailure()).isFalse();
        assertThat(ApiError.from(404, "Not Found").isAuthFailure()).isFalse();
        assertThat(ApiError.from(500, "").isAuthFailure()).isFalse();
    }
}
