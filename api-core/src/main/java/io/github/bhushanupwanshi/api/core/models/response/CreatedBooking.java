package io.github.bhushanupwanshi.api.core.models.response;

/**
 * The body returned by {@code POST /booking}.
 *
 * <p>A separate type because create does not answer in the same shape as read. Observed
 * 2026-10-06:
 *
 * <ul>
 *   <li>{@code POST /booking} → {@code 200} with {@code {"bookingid":N,"booking":{...}}}
 *   <li>{@code GET /booking/{id}} → {@code 200} with the booking <strong>unwrapped</strong>
 * </ul>
 *
 * <p>Note the 200: create does not return 201, which is the obvious assumption and wrong.
 *
 * @param bookingid id the server assigned
 * @param booking   the stored booking, echoed back
 */
public record CreatedBooking(Integer bookingid, Booking booking) {
}
