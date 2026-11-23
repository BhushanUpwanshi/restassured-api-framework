package io.github.bhushanupwanshi.api.core.models.request;

import java.time.LocalDate;

/**
 * The stay window.
 *
 * <p>{@link LocalDate} rather than {@code String}, so an impossible date is a compile or
 * parse failure here rather than a 500 from the server. The wire format is ISO-8601
 * ({@code 2026-11-01}), which is what Jackson's {@code JavaTimeModule} emits by default
 * and what the API returns.
 *
 * <p>Both fields are nullable on purpose: the validation suite needs to send a booking
 * with dates missing, and a non-null constraint here would make that case unexpressible.
 */
public record BookingDates(LocalDate checkin, LocalDate checkout) {

    /** A window starting today. */
    public static BookingDates nights(int nights) {
        LocalDate checkin = LocalDate.now();
        return new BookingDates(checkin, checkin.plusDays(nights));
    }
}
