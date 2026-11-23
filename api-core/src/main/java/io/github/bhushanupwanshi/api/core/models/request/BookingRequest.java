package io.github.bhushanupwanshi.api.core.models.request;

/**
 * A booking payload to send.
 *
 * <p>Every field is nullable, which is deliberate and is the reason this is not the same
 * type as the response model. The validation suite needs to send a booking with
 * {@code firstname} explicitly null, and another with the key absent entirely — the server
 * treats those differently, so the framework has to be able to express both. A model that
 * rejected nulls would make half the negative suite unwritable.
 *
 * <p>Built through {@link #builder()} rather than a six-argument constructor, because
 * {@code new BookingRequest("a", "b", 100, true, dates, null)} is unreadable at the call
 * site and a transposed pair of strings compiles cleanly.
 */
public record BookingRequest(
        String firstname,
        String lastname,
        Integer totalprice,
        Boolean depositpaid,
        BookingDates bookingdates,
        String additionalneeds) {

    public static Builder builder() {
        return new Builder();
    }

    /** This request with one field changed, for deriving a variant from a valid base. */
    public Builder toBuilder() {
        return new Builder()
                .firstname(firstname)
                .lastname(lastname)
                .totalprice(totalprice)
                .depositpaid(depositpaid)
                .bookingdates(bookingdates)
                .additionalneeds(additionalneeds);
    }

    /** Mutable builder for {@link BookingRequest}. */
    public static final class Builder {
        private String firstname;
        private String lastname;
        private Integer totalprice;
        private Boolean depositpaid;
        private BookingDates bookingdates;
        private String additionalneeds;

        public Builder firstname(String value) {
            this.firstname = value;
            return this;
        }

        public Builder lastname(String value) {
            this.lastname = value;
            return this;
        }

        public Builder totalprice(Integer value) {
            this.totalprice = value;
            return this;
        }

        public Builder depositpaid(Boolean value) {
            this.depositpaid = value;
            return this;
        }

        public Builder bookingdates(BookingDates value) {
            this.bookingdates = value;
            return this;
        }

        public Builder additionalneeds(String value) {
            this.additionalneeds = value;
            return this;
        }

        public BookingRequest build() {
            // No validation here on purpose. This type's job is to carry whatever the
            // test wants to send, including payloads the server should reject; checking
            // them here would stop the negative suite before the request was made.
            return new BookingRequest(
                    firstname, lastname, totalprice, depositpaid, bookingdates, additionalneeds);
        }
    }
}
