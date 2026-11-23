package io.github.bhushanupwanshi.api.tests.booking;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;

import io.github.bhushanupwanshi.api.core.data.BookingFactory;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import io.github.bhushanupwanshi.api.tests.data.ApiTestCase;
import io.github.bhushanupwanshi.api.tests.data.Datasets;
import io.github.bhushanupwanshi.api.tests.support.CreatedIds;
import io.github.bhushanupwanshi.api.tests.support.ExpectationCheck;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Negative paths.
 *
 * <p>The payload matrix is data ({@code booking-validation.json}). Every expected status
 * in it was observed against the live service, not assumed. That is why a missing field
 * expects 500 rather than 400: that is what this service does. Cases tagged
 * {@code known-defect} pin behaviour that is wrong (a negative price is accepted) so that
 * a fix shows up as a deliberate test change and not as a silent difference.
 *
 * <p>Auth and routing checks stay in Java: each is one specific call, not a value matrix.
 */
public class ValidationTests extends BaseApiTest {

    // Ids start at 1 and cannot be created out of turn. Not Integer.MAX_VALUE: on this
    // shared host someone else's test created booking 2147483647 (seen 2026-11-23).
    private static final int NEVER_ISSUED_ID = 0;

    @DataProvider(name = "validation")
    public Object[][] validationCases() {
        return Datasets.rows("booking-validation", ApiTestCase.class);
    }

    @Test(dataProvider = "validation", groups = {"regression", "negative"})
    public void aPayloadGetsItsDocumentedResponse(ApiTestCase testCase) {
        nameCase(testCase);

        // Unbound: an accepted-but-invalid payload can come back in a shape the typed
        // model rejects (BV-15 stores the checkin as "0NaN-aN-aN").
        var response = bookings().submitRaw(testCase.request());
        CreatedIds.from(response).ifPresent(this::cleanUpLater);

        ExpectationCheck.verify(testCase.displayName(), response, testCase.expect());
    }

    @Test(groups = {"regression", "negative"})
    public void malformedJsonIsA400NotA500() {
        assertThat(bookings().createFromJson("{\"firstname\": \"Asha\", ")).hasStatus(400).hasBodyContaining("Bad Request");
    }

    @Test(groups = {"regression", "negative", "smoke"})
    public void deletingWithoutATokenIsRefused() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());

        assertThat(bookings().deleteWithToken(created.id(), null)).hasStatus(403).hasBodyContaining("Forbidden");
        assertThat(bookings().getById(created.id())).as("booking survives a refused delete").hasStatus(200);
    }

    @Test(groups = {"regression", "negative"})
    public void deletingWithAnInvalidTokenIsRefused() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());

        assertThat(bookings().deleteWithToken(created.id(), "not-a-real-token")).hasStatus(403);
    }

    @Test(groups = {"regression", "negative"})
    public void readingAnAbsentBookingIs404() {
        assertThat(bookings().getById(NEVER_ISSUED_ID)).hasStatus(404).hasBodyContaining("Not Found");
    }

    @Test(groups = {"regression", "negative"})
    public void aPutMissingRequiredFieldsIsA400UnlikeThePostWhichIsA500() {
        var created = bookingFlow().createAndVerify(BookingFactory.valid());
        BookingRequest onlyAName = BookingRequest.builder().firstname("Only").build();

        // Same defect, two statuses: POST answers 500, PUT answers 400. Pinned so that
        // when one of them is fixed the test says which.
        var response = bookings().update(created.id(), onlyAName);

        assertThat(response).hasStatus(400);
    }

    @Test(groups = {"regression", "negative"})
    public void patchingAnAbsentBookingIs405() {
        assertThat(bookings().partialUpdate(NEVER_ISSUED_ID, Map.of("totalprice", 1))).hasStatus(405);
    }
}
