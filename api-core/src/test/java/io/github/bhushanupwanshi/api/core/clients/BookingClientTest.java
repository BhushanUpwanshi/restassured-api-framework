package io.github.bhushanupwanshi.api.core.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.data.BookingFactory;
import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.github.bhushanupwanshi.api.core.transport.WireMockSupport;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * Every verb, against a stub shaped like the observed API.
 *
 * <p>The stub returns what restful-booker actually returns, quirks included: 200 on create
 * with a wrapped body, 201 on delete, 405 on a repeat delete, plain-text errors. Reproducing
 * the quirks is the point — a stub that returned textbook statuses would let a wrong client
 * pass here and fail against the real service.
 */
public class BookingClientTest extends WireMockSupport {

    private static final String BOOKINGS = "/booking";

    private BookingClient client() {
        AuthTokenCache tokens = new AuthTokenCache(() -> "stub-token");
        return new BookingClient(new Specs(configWithoutRetry(), false), tokens);
    }

    private static String bookingJson(String firstname) {
        return """
                {"firstname":"%s","lastname":"Check","totalprice":111,"depositpaid":true,
                 "bookingdates":{"checkin":"2026-11-01","checkout":"2026-11-05"},
                 "additionalneeds":"Breakfast"}
                """.formatted(firstname);
    }

    @Test
    public void createReturns200WithAWrappedBody() {
        wireMock.stubFor(post(urlEqualTo(BOOKINGS)).willReturn(aResponse()
                .withStatus(200) // not 201
                .withHeader("Content-Type", "application/json")
                .withBody("{\"bookingid\":1151,\"booking\":" + bookingJson("Probe") + "}")));

        var response = client().create(BookingFactory.validNamed("Probe", "Check"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.requireBody().bookingid()).isEqualTo(1151);
        assertThat(response.requireBody().booking().firstname()).isEqualTo("Probe");
    }

    @Test
    public void createSendsTheDatesAsIsoStrings() {
        wireMock.stubFor(post(urlEqualTo(BOOKINGS)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"bookingid\":1,\"booking\":" + bookingJson("A") + "}")));

        BookingRequest booking = BookingRequest.builder()
                .firstname("A").lastname("B").totalprice(100).depositpaid(true)
                .bookingdates(new io.github.bhushanupwanshi.api.core.models.request.BookingDates(
                        java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 5)))
                .build();

        client().create(booking);

        // ISO-8601, not epoch numbers. The Json mapper disables WRITE_DATES_AS_TIMESTAMPS
        // for exactly this; without it the server gets 1762... and rejects it.
        wireMock.verify(postRequestedFor(urlEqualTo(BOOKINGS)).withRequestBody(
                equalToJson("""
                        {"firstname":"A","lastname":"B","totalprice":100,"depositpaid":true,
                         "bookingdates":{"checkin":"2026-11-01","checkout":"2026-11-05"},
                         "additionalneeds":null}
                        """, true, false)));
    }

    @Test
    public void createRawSendsAPayloadWithAKeyAbsent() {
        wireMock.stubFor(post(urlEqualTo(BOOKINGS)).willReturn(aResponse()
                .withStatus(500).withBody("Internal Server Error")));

        Map<String, Object> payload = BookingFactory.withoutField("firstname");
        var response = client().createRaw(payload);

        // 500 on an invalid body, observed. An absent key is a different request from a
        // null one, which is why createRaw exists at all.
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(payload).doesNotContainKey("firstname");
    }

    @Test
    public void getByIdReturnsTheBookingUnwrapped() {
        wireMock.stubFor(get(urlEqualTo(BOOKINGS + "/1151")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(bookingJson("Probe"))));

        var response = client().getById(1151);

        // Note the shape difference from create: no wrapper here.
        assertThat(response.requireBody().firstname()).isEqualTo("Probe");
        assertThat(response.requireBody().bookingdates().checkin())
                .isEqualTo(java.time.LocalDate.of(2026, 11, 1));
    }

    @Test
    public void getByIdReports404WithAPlainTextBody() {
        wireMock.stubFor(get(urlEqualTo(BOOKINGS + "/99999999")).willReturn(aResponse()
                .withStatus(404).withBody("Not Found")));

        var response = client().getById(99999999);

        // Does not throw while parsing a non-JSON error. That was the trap.
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.isSuccessful()).isFalse();
    }

    @Test
    public void getAllIdsReturnsIdsRatherThanBookings() {
        wireMock.stubFor(get(urlEqualTo(BOOKINGS)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("[{\"bookingid\":1},{\"bookingid\":2},{\"bookingid\":3}]")));

        var response = client().getAllIds();

        assertThat(response.requireBody()).hasSize(3);
        assertThat(response.requireBody().get(0).bookingid()).isEqualTo(1);
    }

    @Test
    public void getAllIdsHandlesAnEmptyList() {
        wireMock.stubFor(get(urlEqualTo(BOOKINGS)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));

        assertThat(client().getAllIds().requireBody()).isEmpty();
    }

    @Test
    public void updateSendsTheTokenAndReturnsTheUpdatedBooking() {
        wireMock.stubFor(put(urlEqualTo(BOOKINGS + "/1151")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(bookingJson("Updated"))));

        var response = client().update(1151, BookingFactory.valid());

        assertThat(response.requireBody().firstname()).isEqualTo("Updated");
        wireMock.verify(putRequestedFor(urlEqualTo(BOOKINGS + "/1151")));
        var request = wireMock.findAll(putRequestedFor(urlEqualTo(BOOKINGS + "/1151"))).get(0);
        assertThat(request.getHeader("Cookie")).contains("token=stub-token");
    }

    @Test
    public void partialUpdateSendsOnlyTheGivenFields() {
        wireMock.stubFor(patch(urlEqualTo(BOOKINGS + "/1151")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(bookingJson("Patched"))));

        var response = client().partialUpdate(1151, Map.of("firstname", "Patched"));

        assertThat(response.requireBody().firstname()).isEqualTo("Patched");
        wireMock.verify(patchRequestedFor(urlEqualTo(BOOKINGS + "/1151"))
                .withRequestBody(equalToJson("{\"firstname\":\"Patched\"}")));
    }

    @Test
    public void deleteReturns201BecauseThatIsWhatTheApiDoes() {
        wireMock.stubFor(delete(urlEqualTo(BOOKINGS + "/1151")).willReturn(aResponse()
                .withStatus(201).withBody("Created")));

        var response = client().delete(1151);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(BookingClient.isDeleted(response.statusCode())).isTrue();
        wireMock.verify(deleteRequestedFor(urlEqualTo(BOOKINGS + "/1151")));
    }

    @Test
    public void aRepeatDeleteReportsGoneRatherThanFailure() {
        wireMock.stubFor(delete(urlEqualTo(BOOKINGS + "/1151"))
                .willReturn(aResponse().withStatus(405)));

        var response = client().delete(1151);

        assertThat(response.statusCode()).isEqualTo(405);
        // 405 means it was already gone, which is what cleanup actually cares about.
        assertThat(BookingClient.isDeleted(405)).isTrue();
    }

    @Test
    public void isDeletedRejectsStatusesThatDoNotMeanGone() {
        assertThat(BookingClient.isDeleted(403)).isFalse();
        assertThat(BookingClient.isDeleted(500)).isFalse();
        assertThat(BookingClient.isDeleted(200)).isFalse();
    }

    @Test
    public void writesRefreshTheTokenOnceWhenRefused() {
        wireMock.stubFor(delete(urlPathEqualTo(BOOKINGS + "/1151"))
                .inScenario("stale")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(403).withBody("Forbidden"))
                .willSetStateTo("fresh"));
        wireMock.stubFor(delete(urlPathEqualTo(BOOKINGS + "/1151"))
                .inScenario("stale")
                .whenScenarioStateIs("fresh")
                .willReturn(aResponse().withStatus(201).withBody("Created")));

        assertThat(client().delete(1151).statusCode()).isEqualTo(201);
        wireMock.verify(2, deleteRequestedFor(urlEqualTo(BOOKINGS + "/1151")));
    }
}
