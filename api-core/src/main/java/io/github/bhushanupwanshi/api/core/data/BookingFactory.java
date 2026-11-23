package io.github.bhushanupwanshi.api.core.data;

import io.github.bhushanupwanshi.api.core.models.request.BookingDates;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import net.datafaker.Faker;

/**
 * Builds booking payloads.
 *
 * <p>Generated names rather than {@code "Test User"} everywhere, for one practical reason:
 * when a suite runs in parallel against a shared service, identical fixtures make it
 * impossible to tell which test created which record while reading the data afterwards.
 *
 * <p>Nothing here is random in a way that affects an assertion. A test asserts against the
 * request it sent, not against a hard-coded name, so a generated value cannot make a test
 * pass or fail by chance. Dates are the one exception and are fixed relative to today
 * rather than random, because a check-out before a check-in would be a real difference in
 * what is being tested.
 *
 * <p>{@link Faker} is thread-safe for the generators used here, so one static instance is
 * shared rather than one per call, which is measurably cheaper.
 */
public final class BookingFactory {

    private static final Faker FAKER = new Faker();

    private BookingFactory() {
    }

    /** A valid booking the API will accept. */
    public static BookingRequest valid() {
        return BookingRequest.builder()
                .firstname(FAKER.name().firstName())
                .lastname(FAKER.name().lastName())
                .totalprice(FAKER.number().numberBetween(50, 5_000))
                .depositpaid(FAKER.bool().bool())
                .bookingdates(BookingDates.nights(FAKER.number().numberBetween(1, 14)))
                .additionalneeds(FAKER.options().option("Breakfast", "Lunch", "Dinner", "Late checkout"))
                .build();
    }

    /** A valid booking with a known name, for an assertion that needs a fixed value. */
    public static BookingRequest validNamed(String firstname, String lastname) {
        return valid().toBuilder().firstname(firstname).lastname(lastname).build();
    }

    /**
     * A valid booking with one field set to {@code null}.
     *
     * <p>Distinct from {@link #withoutField}: a present-but-null field and an absent key
     * are different requests, and this API treats them differently.
     *
     * @param field one of {@code firstname}, {@code lastname}, {@code totalprice},
     *              {@code depositpaid}, {@code bookingdates}
     */
    public static BookingRequest withNull(String field) {
        BookingRequest.Builder builder = valid().toBuilder();
        return switch (field) {
            case "firstname" -> builder.firstname(null).build();
            case "lastname" -> builder.lastname(null).build();
            case "totalprice" -> builder.totalprice(null).build();
            case "depositpaid" -> builder.depositpaid(null).build();
            case "bookingdates" -> builder.bookingdates(null).build();
            case "additionalneeds" -> builder.additionalneeds(null).build();
            default -> throw new IllegalArgumentException("Unknown booking field: " + field);
        };
    }

    /**
     * A valid booking as a map, with one key removed entirely.
     *
     * <p>A map rather than {@link BookingRequest}, because an unset record field serialises
     * as {@code null} and so cannot express an absent key. Pairs with
     * {@code BookingClient.createRaw}.
     */
    public static Map<String, Object> withoutField(String field) {
        Map<String, Object> payload = asMap(valid());
        if (payload.remove(field) == null) {
            // Fail loudly: a typo in the field name would otherwise produce a perfectly
            // valid payload, and the negative test would pass for the wrong reason.
            throw new IllegalArgumentException(
                    "Field \"%s\" is not in a booking payload; nothing was removed".formatted(field));
        }
        return payload;
    }

    /** A booking payload as a mutable map, for cases that need to bend the shape. */
    public static Map<String, Object> asMap(BookingRequest booking) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstname", booking.firstname());
        payload.put("lastname", booking.lastname());
        payload.put("totalprice", booking.totalprice());
        payload.put("depositpaid", booking.depositpaid());
        payload.put("additionalneeds", booking.additionalneeds());

        BookingDates dates = booking.bookingdates();
        if (dates != null) {
            payload.put("bookingdates", Map.of(
                    "checkin", String.valueOf(dates.checkin()),
                    "checkout", String.valueOf(dates.checkout())));
        }
        return payload;
    }

    /** A stay in the past, for asserting whatever the API does with one. */
    public static BookingRequest inThePast() {
        LocalDate checkin = LocalDate.now().minusMonths(2);
        return valid().toBuilder()
                .bookingdates(new BookingDates(checkin, checkin.plusDays(3)))
                .build();
    }

    /** A stay whose check-out precedes its check-in. */
    public static BookingRequest withReversedDates() {
        LocalDate checkin = LocalDate.now().plusDays(10);
        return valid().toBuilder()
                .bookingdates(new BookingDates(checkin, checkin.minusDays(5)))
                .build();
    }
}
