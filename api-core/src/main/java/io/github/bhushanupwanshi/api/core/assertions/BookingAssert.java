package io.github.bhushanupwanshi.api.core.assertions;

import io.github.bhushanupwanshi.api.core.models.request.BookingDates;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.models.response.Booking;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertions on a booking, named by field.
 *
 * <p>{@code assertThat(booking).hasFirstname("Bhushan")} fails with
 * "Expected firstname "Bhushan" but was "Bhushn"". The generic
 * {@code isEqualTo(expected)} fails with two record dumps and leaves you to diff them.
 */
public class BookingAssert extends AbstractAssert<BookingAssert, Booking> {

    public BookingAssert(Booking actual) {
        super(actual, BookingAssert.class);
    }

    public BookingAssert hasFirstname(String expected) {
        return field("firstname", expected, notNull().firstname());
    }

    public BookingAssert hasLastname(String expected) {
        return field("lastname", expected, notNull().lastname());
    }

    public BookingAssert hasTotalPrice(int expected) {
        return field("totalprice", expected, notNull().totalprice());
    }

    public BookingAssert isDepositPaid() {
        return field("depositpaid", true, notNull().depositpaid());
    }

    public BookingAssert isNotDepositPaid() {
        return field("depositpaid", false, notNull().depositpaid());
    }

    public BookingAssert hasDates(BookingDates expected) {
        return field("bookingdates", expected, notNull().bookingdates());
    }

    public BookingAssert hasAdditionalNeeds(String expected) {
        return field("additionalneeds", expected, notNull().additionalneeds());
    }

    /**
     * Every field the server returned equals what was sent.
     *
     * <p>Collects all mismatches before failing, so a response that got three fields wrong
     * says so once instead of over three runs.
     */
    public BookingAssert matches(BookingRequest sent) {
        isNotNull();
        List<String> mismatches = new ArrayList<>();
        compare(mismatches, "firstname", sent.firstname(), actual.firstname());
        compare(mismatches, "lastname", sent.lastname(), actual.lastname());
        compare(mismatches, "totalprice", sent.totalprice(), actual.totalprice());
        compare(mismatches, "depositpaid", sent.depositpaid(), actual.depositpaid());
        compare(mismatches, "bookingdates", sent.bookingdates(), actual.bookingdates());
        compare(mismatches, "additionalneeds", sent.additionalneeds(), actual.additionalneeds());
        if (!mismatches.isEmpty()) {
            failWithMessage("Booking does not match what was sent:%n  %s",
                    String.join(System.lineSeparator() + "  ", mismatches));
        }
        return this;
    }

    private Booking notNull() {
        isNotNull();
        return actual;
    }

    private BookingAssert field(String name, Object expected, Object found) {
        if (!Objects.equals(expected, found)) {
            failWithMessage("Expected %s %s but was %s", name, quote(expected), quote(found));
        }
        return this;
    }

    private static void compare(List<String> out, String name, Object expected, Object found) {
        if (!Objects.equals(expected, found)) {
            out.add("%s: sent %s, got %s".formatted(name, quote(expected), quote(found)));
        }
    }

    private static String quote(Object value) {
        return value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
    }
}
