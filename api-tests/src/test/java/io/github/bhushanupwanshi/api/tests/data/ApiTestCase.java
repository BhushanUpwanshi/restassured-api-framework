package io.github.bhushanupwanshi.api.tests.data;

import java.util.List;
import java.util.Map;

/**
 * One request and what the service should do with it.
 *
 * <p>{@code request} is a raw map, not a {@code BookingRequest}, because a case often
 * needs a key to be absent, and a typed model can only say "null". The service treats
 * those two differently, so the data has to be able to say both.
 *
 * <p>{@code toString} is the display name on purpose. TestNG uses it to label each
 * data-provider row, so a failure reads {@code BV-03 rejects ...} and not {@code [2]}.
 */
public record ApiTestCase(
        String id,
        String description,
        List<String> environments,
        List<String> tags,
        Boolean enabled,
        Map<String, Object> request,
        Expectation expect) implements DatasetCase {

    public boolean isTagged(String tag) {
        return tags != null && tags.contains(tag);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
