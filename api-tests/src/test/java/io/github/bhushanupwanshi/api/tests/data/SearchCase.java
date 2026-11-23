package io.github.bhushanupwanshi.api.tests.data;

import java.util.List;
import java.util.Map;

/**
 * Seed a booking, query for it, and say whether it should be found.
 *
 * <p>String values in {@code seed} and {@code query} may contain {@code ${uid}}, which is
 * replaced with one random token per run. The search runs against a shared public host,
 * so a name like "Asha" matches other people's bookings; "Asha-k3j9x" matches only ours.
 *
 * @param seed       booking to create first, or {@code null} to query without seeding
 * @param query      query parameters for {@code GET /booking}
 * @param expectSeed whether the seeded booking's id should be in the results
 * @param expectEmpty whether the result list should be empty
 */
public record SearchCase(
        String id,
        String description,
        List<String> environments,
        List<String> tags,
        Boolean enabled,
        Map<String, Object> seed,
        Map<String, Object> query,
        Boolean expectSeed,
        Boolean expectEmpty) implements DatasetCase {

    @Override
    public String toString() {
        return displayName();
    }
}
