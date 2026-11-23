package io.github.bhushanupwanshi.api.tests.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import java.util.Optional;

/**
 * The booking id in a create response, read from the raw body.
 *
 * <p>For responses that were deliberately not bound to the model, so cleanup still finds
 * what an accepted invalid payload created.
 */
public final class CreatedIds {

    private CreatedIds() {
    }

    public static Optional<Integer> from(ApiResponse<?> response) {
        if (response.statusCode() != 200 || response.rawBody().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode id = Json.mapper().readTree(response.rawBody()).get("bookingid");
            return id != null && id.isInt() ? Optional.of(id.asInt()) : Optional.empty();
        } catch (Exception notJson) {
            return Optional.empty();
        }
    }
}
