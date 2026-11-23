package io.github.bhushanupwanshi.api.tests.bdd.steps;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.bhushanupwanshi.api.core.ApiFramework;
import io.github.bhushanupwanshi.api.core.data.CreatedResourceRegistry;
import io.github.bhushanupwanshi.api.core.flows.BookingFlow;
import io.github.bhushanupwanshi.api.core.models.request.BookingDates;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.models.response.Booking;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.tests.data.ApiTestCase;
import io.github.bhushanupwanshi.api.tests.data.Datasets;
import io.github.bhushanupwanshi.api.tests.support.Api;
import io.github.bhushanupwanshi.api.tests.support.CreatedIds;
import io.github.bhushanupwanshi.api.tests.support.ExpectationCheck;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Steps for the booking features.
 *
 * <p>Steps call {@link BookingFlow} and the clients, the same library the TestNG suites
 * use, and nothing in TestNG. {@code TestLayeringRulesTest} enforces both. Cucumber makes
 * a new instance per scenario, so the fields below are per-scenario state.
 */
public class BookingSteps {

    private final ApiFramework api = Api.framework();
    private final BookingFlow flow = api.bookingFlow();

    private BookingRequest.Builder requested;
    private BookingRequest sent;
    private int bookingId;
    private Booking before;
    private ApiResponse<?> lastResponse;
    private final List<String> datasetFailures = new ArrayList<>();
    private int datasetCases;

    @Given("a guest named {string} wants {int} nights with the deposit {word}")
    public void aGuestWantsNights(String guest, int nights, String deposit) {
        aGuestWantsNights(guest, nights, deposit.equals("paid"));
    }

    @Given("a guest named {string} wants {int} nights with the deposit not paid")
    public void aGuestWantsNightsUnpaid(String guest, int nights) {
        aGuestWantsNights(guest, nights, false);
    }

    private void aGuestWantsNights(String guest, int nights, boolean depositPaid) {
        String[] name = guest.split(" ", 2);
        LocalDate checkin = LocalDate.now().plusDays(30);
        requested = BookingRequest.builder()
                .firstname(name[0]).lastname(name.length > 1 ? name[1] : "Guest")
                .totalprice(100 * nights).depositpaid(depositPaid)
                .bookingdates(new BookingDates(checkin, checkin.plusDays(nights)));
    }

    @And("they ask for {string}")
    public void theyAskFor(String needs) {
        requested.additionalneeds(needs);
    }

    @When("they book the stay")
    public void theyBookTheStay() {
        sent = requested.build();
        var result = flow.createAndVerify(sent);
        bookingId = result.id();
        before = result.stored();
    }

    @Then("the booking is stored exactly as requested")
    public void theBookingIsStoredAsRequested() {
        assertThat(api.bookings().getById(bookingId).requireBody()).matches(sent);
    }

    @Given("an existing booking for {string} at {int}")
    public void anExistingBooking(String guest, int price) {
        aGuestWantsNights(guest, 2, true);
        requested.totalprice(price);
        theyBookTheStay();
    }

    @When("the price is changed to {int}")
    public void thePriceIsChangedTo(int price) {
        flow.updatePriceAndVerify(bookingId, price);
    }

    @Then("the booking costs {int} and the guest's details are unchanged")
    public void theBookingCostsAndDetailsUnchanged(int price) {
        assertThat(api.bookings().getById(bookingId).requireBody())
                .hasTotalPrice(price)
                .hasFirstname(before.firstname())
                .hasLastname(before.lastname())
                .hasDates(before.bookingdates());
    }

    @When("the booking is cancelled")
    public void theBookingIsCancelled() {
        flow.cancelAndVerifyGone(bookingId);
    }

    @Then("the booking is gone")
    public void theBookingIsGone() {
        assertThat(api.bookings().getById(bookingId)).hasStatus(404);
    }

    @When("someone without a token tries to cancel it")
    public void someoneWithoutATokenTriesToCancel() {
        lastResponse = api.bookings().deleteWithToken(bookingId, null);
    }

    @Then("the request is refused with status {int}")
    public void theRequestIsRefused(int status) {
        assertThat(lastResponse).hasStatus(status);
    }

    @And("the booking still exists")
    public void theBookingStillExists() {
        assertThat(api.bookings().getById(bookingId)).hasStatus(200);
    }

    @When("I submit every booking in dataset {string}")
    public void iSubmitEveryBookingInDataset(String dataset) {
        List<ApiTestCase> cases = Datasets.cases(dataset, ApiTestCase.class);
        datasetCases = cases.size();
        for (ApiTestCase testCase : cases) {
            var response = api.bookings().submitRaw(testCase.request());
            CreatedIds.from(response).ifPresent(id ->
                    CreatedResourceRegistry.record("booking", id, api.bookings()::delete));

            List<String> problems = ExpectationCheck.mismatches(response, testCase.expect());
            if (!problems.isEmpty()) {
                datasetFailures.add(testCase.displayName() + ": " + String.join("; ", problems));
            }
        }
    }

    @Then("every case gets its expected response")
    public void everyCaseGetsItsExpectedResponse() {
        assertThat(datasetCases).as("cases run").isPositive();
        assertThat(datasetFailures)
                .as("%d of %d cases failed", datasetFailures.size(), datasetCases)
                .isEmpty();
    }
}
