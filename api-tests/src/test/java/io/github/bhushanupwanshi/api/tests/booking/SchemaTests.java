package io.github.bhushanupwanshi.api.tests.booking;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;

import io.github.bhushanupwanshi.api.core.data.BookingFactory;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * Contract tests: every response shape against a committed JSON schema.
 *
 * <p>The typed models ignore unknown fields on purpose, so a renamed or added field would
 * never fail a functional test. The schemas set {@code additionalProperties: false}, so it
 * fails here, the one place it should.
 */
public class SchemaTests extends BaseApiTest {

    @Test(groups = {"regression", "smoke", "contract"})
    public void createResponseMatchesItsSchema() {
        var created = bookings().create(BookingFactory.valid());
        cleanUpLater(created.requireBody().bookingid());

        assertThat(created).hasStatus(200).matchesSchema("schemas/created-booking.schema.json");
    }

    @Test(groups = {"regression", "contract"})
    public void readResponseMatchesItsSchema() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());

        assertThat(bookings().getById(created.id())).hasStatus(200).matchesSchema("schemas/booking.schema.json");
    }

    @Test(groups = {"regression", "contract"})
    public void updateResponsesMatchTheBookingSchema() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());

        assertThat(bookings().update(created.id(), BookingFactory.valid()))
                .hasStatus(200).matchesSchema("schemas/booking.schema.json");
        assertThat(bookings().partialUpdate(created.id(), Map.of("firstname", "Schema")))
                .hasStatus(200).matchesSchema("schemas/booking.schema.json");
    }

    @Test(groups = {"regression", "contract"})
    public void searchResponseIsIdsOnly() {
        assertThat(bookings().search(Map.of("firstname", "Asha")))
                .hasStatus(200).matchesSchema("schemas/booking-ids.schema.json");
    }
}
