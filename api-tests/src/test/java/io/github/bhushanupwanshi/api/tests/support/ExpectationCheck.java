package io.github.bhushanupwanshi.api.tests.support;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.tests.data.Expectation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks a response against a declarative {@link Expectation}.
 *
 * <p>Collects every mismatch in the expectation and reports them together, so a case that
 * got the status and two fields wrong fails once with three lines. Shared by the TestNG
 * suites and the Cucumber dataset step, which is why it returns a list rather than
 * throwing: the step collects failures across rows.
 */
public final class ExpectationCheck {

    private static final String ABSENT = "<absent>";

    private ExpectationCheck() {
    }

    /** Every way the response differs from the expectation; empty when it matches. */
    public static List<String> mismatches(ApiResponse<?> response, Expectation expect) {
        List<String> out = new ArrayList<>();

        if (expect.status() != null && response.statusCode() != expect.status()) {
            out.add("status: expected %d, got %d (body: %s)"
                    .formatted(expect.status(), response.statusCode(), abbreviate(response.rawBody())));
        }
        if (expect.maxResponseTimeMs() != null
                && response.duration().compareTo(Duration.ofMillis(expect.maxResponseTimeMs())) > 0) {
            out.add("response time: expected at most %d ms, took %d ms"
                    .formatted(expect.maxResponseTimeMs(), response.duration().toMillis()));
        }
        if (expect.bodyContains() != null && !response.rawBody().contains(expect.bodyContains())) {
            out.add("body: expected to contain \"%s\", was %s"
                    .formatted(expect.bodyContains(), abbreviate(response.rawBody())));
        }
        if (expect.schema() != null) {
            try {
                assertThat(response).matchesSchema(expect.schema());
            } catch (AssertionError e) {
                out.add("schema: " + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        if (expect.fields() != null && !expect.fields().isEmpty()) {
            JsonNode body = parse(response.rawBody());
            for (Map.Entry<String, Object> field : expect.fields().entrySet()) {
                Object found = body == null ? ABSENT : valueAt(body, field.getKey());
                if (!sameValue(field.getValue(), found)) {
                    out.add("%s: expected %s, got %s".formatted(field.getKey(), field.getValue(), found));
                }
            }
        }
        return out;
    }

    /** Throws one {@link AssertionError} naming the case and every mismatch. */
    public static void verify(String caseName, ApiResponse<?> response, Expectation expect) {
        List<String> problems = mismatches(response, expect);
        if (!problems.isEmpty()) {
            throw new AssertionError("%s%n  %s".formatted(caseName, String.join(System.lineSeparator() + "  ", problems)));
        }
    }

    /** A value at a dotted path, {@code null} for a JSON null, or {@code <absent>}. */
    static Object valueAt(JsonNode root, String dottedPath) {
        JsonNode node = root;
        for (String part : dottedPath.split("\\.")) {
            if (node == null || !node.has(part)) {
                return ABSENT;
            }
            node = node.get(part);
        }
        if (node.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(node, Object.class);
    }

    private static boolean sameValue(Object expected, Object found) {
        if (expected instanceof Number e && found instanceof Number f) {
            return e.doubleValue() == f.doubleValue();
        }
        return Objects.equals(expected, found);
    }

    private static JsonNode parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : Json.mapper().readTree(raw);
        } catch (Exception notJson) {
            return null;
        }
    }

    private static String abbreviate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "<empty>";
        }
        return raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
    }
}
