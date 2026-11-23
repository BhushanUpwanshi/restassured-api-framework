package io.github.bhushanupwanshi.api.core.flows;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.ApiFramework;
import io.github.bhushanupwanshi.api.core.data.CreatedResourceRegistry;
import io.github.bhushanupwanshi.api.core.models.request.BookingDates;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.transport.WireMockSupport;
import java.time.LocalDate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Flows against a stateful stub.
 *
 * <p>WireMock scenarios make the stub change its answer after a write, the way the real
 * service does, so "the price changed and nothing else did" is something the stub can get
 * right or wrong on purpose.
 */
public class BookingFlowTest extends WireMockSupport {

    private static final BookingRequest SENT = BookingRequest.builder()
            .firstname("Asha").lastname("Rao").totalprice(111).depositpaid(true)
            .bookingdates(new BookingDates(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)))
            .additionalneeds("Breakfast")
            .build();

    private static String json(String firstname, int price) {
        String name = firstname == null ? "null" : "\"" + firstname + "\"";
        return """
                {"firstname":%s,"lastname":"Rao","totalprice":%d,"depositpaid":true,
                 "bookingdates":{"checkin":"2026-11-01","checkout":"2026-11-05"},
                 "additionalneeds":"Breakfast"}
                """.formatted(name, price);
    }

    private BookingFlow flow() {
        wireMock.stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"token\":\"t\"}")));
        return ApiFramework.of(configWithoutRetry(), "u", "p", false).bookingFlow();
    }

    private void stubCreate(int id) {
        wireMock.stubFor(post(urlEqualTo("/booking")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bookingid\":%d,\"booking\":%s}".formatted(id, json("Asha", 111)))));
    }

    private void stubRead(int id, String scenarioState, String body, int status) {
        wireMock.stubFor(get(urlEqualTo("/booking/" + id))
                .inScenario("booking").whenScenarioStateIs(scenarioState)
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", status == 200 ? "application/json" : "text/plain")
                        .withBody(body)));
    }

    @AfterMethod(alwaysRun = true)
    public void forgetRegistrations() {
        CreatedResourceRegistry.discard();
    }

    @Test
    public void createAndVerifyReturnsTheStoredBookingAndRegistersItForCleanup() {
        stubCreate(7);
        stubRead(7, STARTED, json("Asha", 111), 200);

        var result = flow().createAndVerify(SENT);

        assertThat(result.id()).isEqualTo(7);
        assertThat(result.stored().firstname()).isEqualTo("Asha");
        assertThat(CreatedResourceRegistry.pending()).isEqualTo(1);
    }

    @Test
    public void createAndVerifyNamesTheFieldTheServerStoredWrongly() {
        stubCreate(8);
        stubRead(8, STARTED, json("Ash", 111), 200);

        assertThatThrownBy(() -> flow().createAndVerify(SENT))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("firstname: sent \"Asha\", got \"Ash\"");
    }

    @Test
    public void aFailedReadBackStillLeavesTheBookingRegisteredForCleanup() {
        stubCreate(9);
        stubRead(9, STARTED, "Not Found", 404);

        assertThatThrownBy(() -> flow().createAndVerify(SENT))
                .hasMessageContaining("read-back of booking 9")
                .hasMessageContaining("Expected HTTP 200 but got 404");

        // Registered before the read-back, so the orphan still gets deleted.
        assertThat(CreatedResourceRegistry.pending()).isEqualTo(1);
    }

    @Test
    public void updatePriceAndVerifyPassesWhenOnlyThePriceChanged() {
        stubRead(5, STARTED, json("Asha", 111), 200);
        wireMock.stubFor(patch(urlEqualTo("/booking/5")).inScenario("booking")
                .whenScenarioStateIs(STARTED).willSetStateTo("patched")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(json("Asha", 222))));
        stubRead(5, "patched", json("Asha", 222), 200);

        var after = flow().updatePriceAndVerify(5, 222);

        assertThat(after.totalprice()).isEqualTo(222);
    }

    @Test
    public void updatePriceAndVerifyCatchesAPatchThatWipedOtherFields() {
        stubRead(6, STARTED, json("Asha", 111), 200);
        wireMock.stubFor(patch(urlEqualTo("/booking/6")).inScenario("booking")
                .whenScenarioStateIs(STARTED).willSetStateTo("patched")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(json(null, 222))));
        // A PATCH behaving like a PUT: price right, firstname gone.
        stubRead(6, "patched", json(null, 222), 200);

        assertThatThrownBy(() -> flow().updatePriceAndVerify(6, 222))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("firstname");
    }

    @Test
    public void cancelAndVerifyGoneChecksTheReadNotJustTheDeleteStatus() {
        wireMock.stubFor(delete(urlEqualTo("/booking/4")).inScenario("booking")
                .whenScenarioStateIs(STARTED).willSetStateTo("deleted")
                .willReturn(aResponse().withStatus(201).withBody("Created")));
        stubRead(4, "deleted", "Not Found", 404);

        flow().cancelAndVerifyGone(4);
    }

    @Test
    public void cancelAndVerifyGoneFailsWhenTheBookingIsStillReadable() {
        wireMock.stubFor(delete(urlEqualTo("/booking/3"))
                .willReturn(aResponse().withStatus(201).withBody("Created")));
        stubRead(3, STARTED, json("Asha", 111), 200);

        assertThatThrownBy(() -> flow().cancelAndVerifyGone(3))
                .hasMessageContaining("read of deleted booking 3")
                .hasMessageContaining("Expected HTTP 404 but got 200");
    }
}
