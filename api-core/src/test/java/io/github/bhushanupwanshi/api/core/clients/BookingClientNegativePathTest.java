package io.github.bhushanupwanshi.api.core.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.github.bhushanupwanshi.api.core.transport.WireMockSupport;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

/** The calls that exist so negative tests can send what a well-behaved client would not. */
public class BookingClientNegativePathTest extends WireMockSupport {

    private final AtomicInteger logins = new AtomicInteger();

    private BookingClient client() {
        AuthTokenCache tokens = new AuthTokenCache(() -> "real-" + logins.incrementAndGet());
        return new BookingClient(new Specs(configWithoutRetry(), false), tokens);
    }

    @Test
    public void deleteWithTokenSendsNoCookieForNullAndDoesNotRefreshOn403() {
        wireMock.stubFor(delete(urlEqualTo("/booking/1")).willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        var response = client().deleteWithToken(1, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(logins.get()).as("logins").isZero();
        wireMock.verify(1, deleteRequestedFor(urlEqualTo("/booking/1")).withCookie("token", absent()));
    }

    @Test
    public void deleteWithTokenSendsExactlyTheTokenGiven() {
        wireMock.stubFor(delete(urlEqualTo("/booking/2")).willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        client().deleteWithToken(2, "bad");

        wireMock.verify(deleteRequestedFor(urlEqualTo("/booking/2")).withCookie("token", equalTo("bad")));
    }

    @Test
    public void createFromJsonSendsTheBodyUntouched() {
        wireMock.stubFor(post(urlEqualTo("/booking")).willReturn(aResponse().withStatus(400).withBody("Bad Request")));

        var response = client().createFromJson("{not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isNull();
        wireMock.verify(postRequestedFor(urlEqualTo("/booking"))
                .withRequestBody(equalTo("{not json")));
    }

    @Test
    public void searchPassesFiltersAsQueryParametersAndBindsIds() {
        wireMock.stubFor(get(urlPathEqualTo("/booking"))
                .withQueryParam("firstname", equalTo("Asha"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"bookingid\":7},{\"bookingid\":9}]")));

        var response = client().search(Map.of("firstname", "Asha"));

        assertThat(response.requireBody()).extracting(b -> b.bookingid()).containsExactly(7, 9);
    }

    @Test
    public void requestsAcceptExactlyApplicationJson() {
        // restful-booker answers 418 to REST Assured's default multi-type Accept list.
        wireMock.stubFor(post(urlEqualTo("/booking")).willReturn(aResponse().withStatus(500)));

        client().createFromJson("{}");

        wireMock.verify(postRequestedFor(urlEqualTo("/booking")).withHeader("Accept", equalTo("application/json")));
    }

    @Test
    public void searchWithNoMatchIsAnEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/booking")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("[]")));

        assertThat(client().search(Map.of("firstname", "nobody")).requireBody()).isEmpty();
    }
}
