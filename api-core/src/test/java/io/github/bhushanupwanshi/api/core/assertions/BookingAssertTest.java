package io.github.bhushanupwanshi.api.core.assertions;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.models.request.BookingDates;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.models.response.Booking;
import java.time.LocalDate;
import org.testng.annotations.Test;

public class BookingAssertTest {

    private static final BookingDates DATES =
            new BookingDates(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5));

    private static final Booking STORED = new Booking("Asha", "Rao", 111, true, DATES, "Breakfast");

    @Test
    public void fieldAssertionsChain() {
        assertThatCode(() -> assertThat(STORED)
                .hasFirstname("Asha").hasLastname("Rao").hasTotalPrice(111)
                .isDepositPaid().hasDates(DATES).hasAdditionalNeeds("Breakfast"))
                .doesNotThrowAnyException();
    }

    @Test
    public void aFieldMismatchNamesTheField() {
        assertThatThrownBy(() -> assertThat(STORED).hasFirstname("Asa"))
                .hasMessageContaining("Expected firstname \"Asa\" but was \"Asha\"");
    }

    @Test
    public void matchesReportsEveryMismatchAtOnce() {
        BookingRequest sent = BookingRequest.builder()
                .firstname("Asha").lastname("Roy").totalprice(222).depositpaid(true)
                .bookingdates(DATES).additionalneeds("Breakfast").build();

        assertThatThrownBy(() -> assertThat(STORED).matches(sent))
                .hasMessageContaining("lastname: sent \"Roy\", got \"Rao\"")
                .hasMessageContaining("totalprice: sent 222, got 111")
                .hasMessageNotContaining("firstname");
    }
}
