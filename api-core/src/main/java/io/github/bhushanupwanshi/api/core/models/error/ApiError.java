package io.github.bhushanupwanshi.api.core.models.error;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bhushanupwanshi.api.core.serialization.Json;

/**
 * A failure response, normalised.
 *
 * <p>Deliberately tolerant about the body, because the target API is not consistent
 * about it. Observed against restful-booker on 2026-10-06:
 *
 * <ul>
 *   <li>404 returns the plain text {@code Not Found}, with no JSON at all
 *   <li>403 returns the plain text {@code Forbidden}
 *   <li>{@code /auth} with bad credentials returns <strong>HTTP 200</strong> and
 *       {@code {"reason":"Bad credentials"}}
 * </ul>
 *
 * <p>A model that assumed a JSON error envelope would throw while parsing the error,
 * which replaces a clear 404 with a confusing deserialization failure. Real services
 * behave like this more often than their documentation suggests.
 *
 * @param statusCode HTTP status the server returned
 * @param reason     best available explanation: the JSON {@code reason} field if there
 *                   is one, otherwise the body as text
 * @param rawBody    body exactly as received, for a failure message that leaves nothing out
 */
public record ApiError(int statusCode, String reason, String rawBody) {

    private static final String REASON_FIELD = "reason";

    /**
     * Builds an error from whatever the server sent.
     *
     * @param statusCode HTTP status
     * @param rawBody    response body, possibly empty, possibly not JSON
     */
    public static ApiError from(int statusCode, String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        return new ApiError(statusCode, extractReason(body, statusCode), body);
    }

    /**
     * Whether this looks like an authentication or authorisation failure.
     *
     * <p>Includes 403 because restful-booker answers a missing or stale token with
     * {@code 403 Forbidden} rather than 401. Checking only 401, which is what the
     * textbook says, would never trigger a token refresh against this API.
     */
    public boolean isAuthFailure() {
        return statusCode == 401 || statusCode == 403;
    }

    private static String extractReason(String body, int statusCode) {
        if (body.isBlank()) {
            return "HTTP " + statusCode + " with an empty body";
        }
        try {
            JsonNode node = Json.mapper().readTree(body);
            if (node.hasNonNull(REASON_FIELD)) {
                return node.get(REASON_FIELD).asText();
            }
        } catch (Exception e) {
            // Not JSON. Expected for this API's 403 and 404, so not worth logging:
            // the body is kept verbatim below and says everything it has to say.
        }
        return body.strip();
    }
}
