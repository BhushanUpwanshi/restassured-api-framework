package io.github.bhushanupwanshi.api.core.flows;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;

import io.github.bhushanupwanshi.api.core.clients.BookingClient;
import io.github.bhushanupwanshi.api.core.data.CreatedResourceRegistry;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.models.response.Booking;
import java.util.Map;

/**
 * Booking operations as a business would describe them.
 *
 * <p>A client is one HTTP call. A flow is "create a booking and make sure it really
 * exists", which is three things: the create, a read-back, and registering the booking
 * for cleanup. Tests and Cucumber steps call flows, so they read as intent rather than as
 * a list of requests.
 *
 * <p>Every step is verified as it goes. When a flow fails, it fails at the step that broke
 * ("read-back after create returned 404"), not three steps later on a null.
 *
 * <p>This class may not import {@code transport} except for the response envelope, and it
 * never builds a request. ArchUnit enforces that in {@code LayeringRulesTest}.
 */
public final class BookingFlow {

    private final BookingClient bookings;

    public BookingFlow(BookingClient bookings) {
        this.bookings = bookings;
    }

    /** What a successful create leaves behind: the id and the booking as stored. */
    public record CreatedAndStored(int id, Booking stored) {
    }

    /**
     * Creates a booking, registers it for cleanup, and confirms a read-back matches what
     * was sent.
     *
     * <p>Registration happens straight after the create and before the read-back, so a
     * failed read-back still leaves nothing behind.
     */
    public CreatedAndStored createAndVerify(BookingRequest booking) {
        var created = bookings.create(booking);
        assertThat(created).as("create booking").hasStatus(200);

        int id = created.requireBody().bookingid();
        CreatedResourceRegistry.record("booking", id, bookings::delete);

        var readBack = bookings.getById(id);
        assertThat(readBack).as("read-back of booking %d", id).hasStatus(200);
        assertThat(readBack.requireBody()).as("booking %d as stored", id).matches(booking);

        return new CreatedAndStored(id, readBack.requireBody());
    }

    /**
     * Changes the price, then confirms the change stuck and nothing else moved.
     *
     * <p>The "nothing else moved" half is what catches a PATCH implemented as a PUT, which
     * silently nulls every field the request did not mention.
     */
    public Booking updatePriceAndVerify(int id, int newPrice) {
        Booking before = bookings.getById(id).requireBody();

        var patched = bookings.partialUpdate(id, Map.of("totalprice", newPrice));
        assertThat(patched).as("patch price of booking %d", id).hasStatus(200);

        Booking after = bookings.getById(id).requireBody();
        assertThat(after).as("booking %d after price change", id)
                .hasTotalPrice(newPrice)
                .hasFirstname(before.firstname())
                .hasLastname(before.lastname())
                .hasDates(before.bookingdates());
        return after;
    }

    /** Replaces the whole booking and confirms the stored copy matches the new one. */
    public Booking replaceAndVerify(int id, BookingRequest replacement) {
        var updated = bookings.update(id, replacement);
        assertThat(updated).as("replace booking %d", id).hasStatus(200);

        Booking stored = bookings.getById(id).requireBody();
        assertThat(stored).as("booking %d after replace", id).matches(replacement);
        return stored;
    }

    /**
     * Deletes a booking and confirms it can no longer be read.
     *
     * <p>Checks the read, not just the delete status. This API answers a delete with 201,
     * so the status alone proves very little.
     */
    public void cancelAndVerifyGone(int id) {
        var deleted = bookings.delete(id);
        if (!BookingClient.isDeleted(deleted.statusCode())) {
            throw new AssertionError("Delete of booking %d returned %d: %s"
                    .formatted(id, deleted.statusCode(), deleted.rawBody()));
        }
        assertThat(bookings.getById(id)).as("read of deleted booking %d", id).hasStatus(404);
    }
}
