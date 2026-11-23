package io.github.bhushanupwanshi.api.tests.data;

import java.util.Map;

/**
 * What a case expects back.
 *
 * @param status            HTTP status, required
 * @param schema            classpath schema the raw body must match, or {@code null} for none
 * @param fields            dotted paths into the response body and their expected values,
 *                          e.g. {@code "booking.totalprice": null}. A {@code null} value
 *                          means the field is present and null, not that it is absent
 * @param bodyContains      text the raw body must contain, for plain-text error bodies
 * @param maxResponseTimeMs upper bound on the round trip, or {@code null} for none
 */
public record Expectation(
        Integer status,
        String schema,
        Map<String, Object> fields,
        String bodyContains,
        Long maxResponseTimeMs) {
}
