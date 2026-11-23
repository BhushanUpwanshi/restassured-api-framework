package io.github.bhushanupwanshi.api.core.transport;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A response, with the body already bound to a type.
 *
 * <p>Exists so tests assert on objects rather than on {@code jsonPath().getString(...)}
 * strings. Three things follow from that:
 *
 * <ul>
 *   <li>a typo in a field name is a compile error, not a null at runtime
 *   <li>deserialization belongs to the client layer, so no test ever touches a mapper
 *   <li>{@link #duration()} makes a response-time assertion an ordinary assertion
 * </ul>
 *
 * <p>{@code duration} is REST Assured's own measurement rather than something this
 * framework times. Wrapping a stopwatch around the call would measure the filter chain
 * and the deserialization too, which is not what an SLA is about.
 *
 * <p>With retries in play this is the <em>last</em> attempt's time, which is the honest
 * number for "how long does this endpoint take": the earlier attempts failed, so their
 * duration says nothing about the endpoint's behaviour when it works.
 *
 * @param body       deserialized body, or {@code null} for an empty or unparsed response
 * @param statusCode HTTP status
 * @param headers    response headers, lower-cased keys
 * @param duration   round-trip time of the attempt that produced this response
 * @param rawBody    body as received, kept for error messages and schema validation
 * @param <T>        type the body was bound to
 */
public record ApiResponse<T>(
        T body,
        int statusCode,
        Map<String, String> headers,
        Duration duration,
        String rawBody) {

    /**
     * Wraps a REST Assured response, binding the body with the supplied function.
     *
     * @param response REST Assured response
     * @param binder   turns the raw body into {@code T}; not called for an empty body
     */
    public static <T> ApiResponse<T> from(Response response, Function<String, T> binder) {
        String raw = response.getBody() == null ? "" : response.getBody().asString();
        T bound = raw.isBlank() ? null : binder.apply(raw);

        Map<String, String> headers = response.getHeaders().asList().stream()
                .collect(Collectors.toMap(
                        header -> header.getName().toLowerCase(),
                        io.restassured.http.Header::getValue,
                        // A header can legitimately repeat (Set-Cookie). Keep the first
                        // rather than throwing, which is what toMap does by default.
                        (first, ignored) -> first));

        return new ApiResponse<>(
                bound,
                response.getStatusCode(),
                Map.copyOf(headers),
                Duration.ofMillis(response.timeIn(TimeUnit.MILLISECONDS)),
                raw);
    }

    /** Wraps a response without binding a body, for calls that return nothing useful. */
    public static ApiResponse<Void> from(Response response) {
        return from(response, raw -> null);
    }

    /** Whether the status is 2xx. */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** A header value, case-insensitively. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase()));
    }

    /**
     * The body, or a failure naming the status and what came back instead.
     *
     * <p>For the common case of a test that only makes sense if the call succeeded.
     * Without it, the first symptom is a {@code NullPointerException} several lines
     * later with nothing about the 401 that caused it.
     */
    public T requireBody() {
        if (body == null) {
            throw new IllegalStateException(
                    "Expected a body but got none. Status %d, raw body: %s"
                            .formatted(statusCode, rawBody.isBlank() ? "<empty>" : rawBody));
        }
        return body;
    }
}
