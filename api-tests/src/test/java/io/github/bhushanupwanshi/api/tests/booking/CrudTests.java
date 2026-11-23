package io.github.bhushanupwanshi.api.tests.booking;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.data.BookingFactory;
import io.github.bhushanupwanshi.api.core.models.response.BookingId;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import io.github.bhushanupwanshi.api.tests.data.ApiTestCase;
import io.github.bhushanupwanshi.api.tests.data.Datasets;
import io.github.bhushanupwanshi.api.tests.support.ExpectationCheck;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Create, read, update and delete.
 *
 * <p>The create matrix is data ({@code testdata/<env>/booking-crud.json}): adding a row
 * adds a test, with no Java change. The lifecycle tests stay in Java because they are
 * about order (create, then change, then delete), and order in JSON turns into a script
 * nobody can read.
 */
public class CrudTests extends BaseApiTest {

    @DataProvider(name = "crud")
    public Object[][] crudCases() {
        return Datasets.rows("booking-crud", ApiTestCase.class);
    }

    @Test(dataProvider = "crud", groups = {"regression", "smoke"})
    public void aValidBookingIsStoredExactlyAsSent(ApiTestCase testCase) {
        nameCase(testCase);

        var created = bookings().createRaw(testCase.request());
        if (created.body() != null) {
            cleanUpLater(created.body().bookingid());
        }
        ExpectationCheck.verify(testCase.displayName(), created, testCase.expect());

        int id = created.requireBody().bookingid();
        var stored = bookings().getById(id);
        assertThat(stored).hasStatus(200).matchesSchema("schemas/booking.schema.json");

        // Compare what was sent with what was stored, field by field, through JSON, so
        // the dataset's raw map and the typed response meet on the same ground.
        Map<?, ?> storedAsMap = Json.mapper().convertValue(stored.requireBody(), Map.class);
        testCase.request().forEach((field, sent) ->
                assertThat(storedAsMap.get(field)).as("%s: stored %s", testCase.id(), field).isEqualTo(sent));
    }

    @Test(groups = {"regression", "smoke"})
    public void aBookingCanBeCreatedChangedAndCancelled() {
        var flow = bookingFlow();

        var created = flow.createAndVerify(BookingFactory.valid());
        flow.updatePriceAndVerify(created.id(), created.stored().totalprice() + 75);
        flow.replaceAndVerify(created.id(), BookingFactory.validNamed("Replaced", "Guest"));
        flow.cancelAndVerifyGone(created.id());
    }

    @Test(groups = "regression")
    public void aNewBookingAppearsInTheListOfIds() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());

        var all = bookings().getAllIds();

        assertThat(all).hasStatus(200).matchesSchema("schemas/booking-ids.schema.json");
        assertThat(all.requireBody()).extracting(BookingId::bookingid).contains(created.id());
    }

    @Test(groups = "regression")
    public void aPartialUpdateChangesOnlyTheFieldsItNames() {
        var created = bookingFlow().createAndVerify(BookingFactory.validNamed("Patch", "Target"));

        var patched = bookings().partialUpdate(created.id(), Map.of("additionalneeds", "Airport pickup"));

        assertThat(patched).hasStatus(200);
        assertThat(patched.requireBody())
                .hasAdditionalNeeds("Airport pickup")
                .hasFirstname("Patch")
                .hasLastname("Target")
                .hasTotalPrice(created.stored().totalprice());
    }

    @Test(groups = "regression")
    public void aDeletedBookingCannotBeDeletedAgain() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());
        bookingFlow().cancelAndVerifyGone(created.id());

        // 405, not 404: observed 2026-10-06 and again 2026-11-23.
        assertThat(bookings().delete(created.id())).hasStatus(405);
    }
}
