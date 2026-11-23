package io.github.bhushanupwanshi.api.core.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.github.bhushanupwanshi.api.core.transport.WireMockSupport;
import io.restassured.response.Response;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

/**
 * Token handling on authenticated calls: the cookie, and the single refresh.
 */
public class AuthenticatedClientTest extends WireMockSupport {

    private static final String PATH = "/protected";

    /** A minimal concrete client, since the class under test is abstract. */
    private static final class TestClient extends AuthenticatedClient {
        TestClient(Specs specs, AuthTokenCache tokens) {
            super(specs, tokens);
        }

        Response fetch() {
            return sendAuthenticated("GET " + PATH, token ->
                    request().cookie(TOKEN_COOKIE, token).get(PATH));
        }
    }

    private AtomicInteger logins;

    private TestClient clientWithTokens() {
        logins = new AtomicInteger();
        AuthTokenCache tokens = new AuthTokenCache(() -> "token-" + logins.incrementAndGet());
        return new TestClient(new Specs(configWithoutRetry(), false), tokens);
    }

    @Test
    public void sendsTheTokenAsACookie() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThat(clientWithTokens().fetch().getStatusCode()).isEqualTo(200);

        // Cookie, not Authorization: Bearer. Bearer is the obvious guess and this API
        // answers it with 403, which was verified before any of this was written.
        var request = wireMock.findAll(getRequestedFor(urlEqualTo(PATH))).get(0);
        assertThat(request.getHeader("Cookie")).contains("token=token-1");
    }

    @Test
    public void refreshesTheTokenOnceWhenRefusedThenSucceeds() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("expiry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403).withBody("Forbidden"))
                .willSetStateTo("fresh"));
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("expiry")
                .whenScenarioStateIs("fresh")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        TestClient client = clientWithTokens();
        assertThat(client.fetch().getStatusCode()).isEqualTo(200);

        // Two requests, two logins: the stale token was discarded and a fresh one used.
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
        assertThat(logins.get()).isEqualTo(2);

        var requests = wireMock.findAll(getRequestedFor(urlEqualTo(PATH)));
        assertThat(requests.get(0).getHeader("Cookie")).contains("token-1");
        assertThat(requests.get(1).getHeader("Cookie")).contains("token-2");
    }

    /**
     * The failure mode this guards against is recursion.
     *
     * <p>A refresh that calls itself turns one expired token into an unbounded login storm
     * against the auth endpoint. Exactly one retry, then the real 403 is handed back.
     */
    @Test
    public void refreshesOnlyOnceWhenTheFreshTokenIsAlsoRefused() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        TestClient client = clientWithTokens();
        Response response = client.fetch();

        assertThat(response.getStatusCode()).isEqualTo(403);
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
        assertThat(logins.get()).isEqualTo(2);
    }

    @Test
    public void refreshesOnA401AsWellAsA403() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("401")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("ok"));
        wireMock.stubFor(get(urlEqualTo(PATH))
                .inScenario("401")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThat(clientWithTokens().fetch().getStatusCode()).isEqualTo(200);
        wireMock.verify(2, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    public void doesNotRefreshOnOtherFailures() {
        wireMock.stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(404).withBody("Not Found")));

        TestClient client = clientWithTokens();
        assertThat(client.fetch().getStatusCode()).isEqualTo(404);

        // A 404 is not an auth problem. Logging in again would waste a call and
        // obscure the actual failure.
        wireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
        assertThat(logins.get()).isEqualTo(1);
    }

    @Test
    public void logsInOnlyOnceAcrossSeveralSuccessfulCalls() {
        wireMock.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        TestClient client = clientWithTokens();
        client.fetch();
        client.fetch();
        client.fetch();

        wireMock.verify(3, getRequestedFor(urlEqualTo(PATH)));
        assertThat(logins.get()).isEqualTo(1);
    }
}
