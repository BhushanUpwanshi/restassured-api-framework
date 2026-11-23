package io.github.bhushanupwanshi.api.core.assertions;

import io.github.bhushanupwanshi.api.core.models.response.Booking;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;

/**
 * Static entry points for the framework's assertions.
 *
 * <p>Named {@code ApiAssertions} rather than {@code Assertions} so a test can import this
 * and AssertJ's own {@code Assertions.assertThat} side by side without one shadowing the
 * other. Overload resolution picks the right one by argument type.
 */
public final class ApiAssertions {

    private ApiAssertions() {
    }

    public static ApiResponseAssert assertThat(ApiResponse<?> response) {
        return new ApiResponseAssert(response);
    }

    public static BookingAssert assertThat(Booking booking) {
        return new BookingAssert(booking);
    }
}
