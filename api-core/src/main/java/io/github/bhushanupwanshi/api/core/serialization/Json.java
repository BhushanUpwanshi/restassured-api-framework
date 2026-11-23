package io.github.bhushanupwanshi.api.core.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The one configured {@link ObjectMapper} for the framework.
 *
 * <p>Single instance because {@code ObjectMapper} is thread-safe once configured and
 * expensive to build. More importantly, one instance means one set of rules: a client
 * that quietly built its own would serialize the same payload differently, and the
 * resulting failure looks like a server bug.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // Responses carry fields the tests do not model, and new ones appear
            // without warning. Failing on them would turn every server-side addition
            // into a suite-wide outage. Contract drift is caught deliberately by the
            // JSON-schema tests instead, which is where that belongs.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Dates as ISO-8601 strings, not epoch numbers.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Deliberately NOT setSerializationInclusion(NON_NULL). A null field and an absent
    // field are different requests, and the validation suite needs both: "firstname"
    // present-but-null tests one server behaviour, omitting the key tests another.
    // Suppressing nulls globally would silently collapse those two cases into one.

    private Json() {
    }

    /** The shared mapper, for callers that need it directly. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializes a value to JSON.
     *
     * @throws IllegalArgumentException wrapping the Jackson failure, which is a
     *     programming error here rather than something a test should handle
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize " + value.getClass().getName(), e);
        }
    }

    /**
     * Deserializes JSON into a type.
     *
     * @throws IllegalArgumentException with the offending payload included, because a
     *     binding failure is almost always about what the server actually sent
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Could not deserialize into %s. Payload: %s".formatted(type.getName(), json), e);
        }
    }
}
