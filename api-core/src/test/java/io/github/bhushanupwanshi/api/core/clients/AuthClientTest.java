package io.github.bhushanupwanshi.api.core.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.models.response.AuthResponse;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.github.bhushanupwanshi.api.core.transport.WireMockSupport;
import org.testng.annotations.Test;

/**
 * The auth client, against a stub shaped like the real endpoint.
 *
 * <p>The stub reproduces the quirk that matters: {@code /auth} answers <strong>HTTP 200
 * for a rejected login</strong>, with {@code reason} instead of {@code token}. Verified
 * against restful-booker on 2026-10-06. A client that trusted the status code would treat
 * every wrong password as a success holding a null token.
 */
public class AuthClientTest extends WireMockSupport {

    private static final String AUTH = "/auth";

    private AuthClient client() {
        return new AuthClient(new Specs(configWithoutRetry(), false));
    }

    private void stubToken(String token) {
        wireMock.stubFor(post(urlEqualTo(AUTH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"token\":\"" + token + "\"}")));
    }

    private void stubRejection() {
        wireMock.stubFor(post(urlEqualTo(AUTH)).willReturn(aResponse()
                .withStatus(200) // yes, 200
                .withHeader("Content-Type", "application/json")
                .withBody("{\"reason\":\"Bad credentials\"}")));
    }

    @Test
    public void returnsTheTokenForValidCredentials() {
        stubToken("abc123");

        assertThat(client().requestToken("admin", "password123")).isEqualTo("abc123");
    }

    @Test
    public void postsTheCredentialsAsJson() {
        stubToken("abc123");

        client().requestToken("admin", "password123");

        wireMock.verify(postRequestedFor(urlEqualTo(AUTH))
                .withRequestBody(equalToJson("{\"username\":\"admin\",\"password\":\"password123\"}")));
    }

    @Test
    public void reportsARejectionDespiteTheSuccessStatus() {
        stubRejection();

        ApiResponse<AuthResponse> response = client().authenticate("admin", "wrong");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.requireBody().isSuccessful()).isFalse();
        assertThat(response.requireBody().reason()).isEqualTo("Bad credentials");
    }

    @Test
    public void requestTokenFailsWithTheReasonTheServerGave() {
        stubRejection();

        // "Bad credentials", not a null token surfacing as a 403 three calls later.
        assertThatThrownBy(() -> client().requestToken("admin", "wrong"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bad credentials")
                .hasMessageContaining("admin");
    }

    @Test
    public void failsClearlyWhenTheBodyIsEmpty() {
        wireMock.stubFor(post(urlEqualTo(AUTH)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client().requestToken("admin", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected a body");
    }

    @Test
    public void treatsAnEmptyTokenAsAFailure() {
        wireMock.stubFor(post(urlEqualTo(AUTH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"token\":\"\"}")));

        assertThatThrownBy(() -> client().requestToken("admin", "password123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void cacheObtainsATokenOnceAcrossManyReads() {
        stubToken("abc123");

        // The credential-taking overload, not cache(): these tests run against a stub
        // and must not require the real environment to be configured.
        var cache = client().cache("admin", "password123");
        assertThat(cache.token()).isEqualTo("abc123");
        assertThat(cache.token()).isEqualTo("abc123");
        assertThat(cache.token()).isEqualTo("abc123");

        wireMock.verify(1, postRequestedFor(urlEqualTo(AUTH)));
    }
}
