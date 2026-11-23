package io.github.bhushanupwanshi.api.core.models.response;

/**
 * One entry from {@code GET /booking}.
 *
 * <p>That endpoint returns {@code [{"bookingid":1},{"bookingid":2}, ...]} — ids only, not
 * full bookings. Worth a type rather than a bare int list, because the distinction
 * between "the ids" and "the bookings" is the kind of thing a test gets wrong once.
 *
 * @param bookingid the id
 */
public record BookingId(Integer bookingid) {
}
