package io.github.bhushanupwanshi.api.core.models.response;

import io.github.bhushanupwanshi.api.core.models.request.BookingDates;

/**
 * A booking as the API returns it.
 *
 * <p>Separate from {@code BookingRequest} even though the fields line up. They are not
 * the same thing: a request may legitimately carry nulls to exercise validation, while a
 * response is what the server decided to store. Collapsing them into one type makes the
 * validation suite impossible to write without weakening the response model.
 *
 * @param firstname       guest's first name
 * @param lastname        guest's last name
 * @param totalprice      total price. Field names match the wire format, which is
 *                        lower-case and unseparated, so no Jackson annotations are needed
 * @param depositpaid     whether a deposit was taken
 * @param bookingdates    the stay window
 * @param additionalneeds free text, absent on many bookings
 */
public record Booking(
        String firstname,
        String lastname,
        Integer totalprice,
        Boolean depositpaid,
        BookingDates bookingdates,
        String additionalneeds) {
}
