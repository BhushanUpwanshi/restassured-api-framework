package io.github.bhushanupwanshi.api.core.clients;

import io.github.bhushanupwanshi.api.core.config.Secrets;
import io.github.bhushanupwanshi.api.core.models.response.AuthResponse;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import java.util.Map;

/**
 * Obtains auth tokens.
 *
 * <p>Extends {@link ApiClient} rather than {@link AuthenticatedClient}, because this is
 * the client that produces tokens and so cannot require one.
 */
public final class AuthClient extends ApiClient {

    public AuthClient(Specs specs) {
        super(specs);
    }

    /**
     * Posts credentials and returns whatever came back, success or rejection.
     *
     * <p>For tests that assert on a failed login. Note the status is 200 either way, so
     * {@link AuthResponse#isSuccessful()} is the check that means anything here.
     */
    public ApiResponse<AuthResponse> authenticate(String username, String password) {
        var response = send("POST " + specs.config().authPath(), () -> request()
                .body(Map.of("username", username, "password", password))
                .post(specs.config().authPath()));

        return bind(response, AuthResponse.class);
    }

    /**
     * Obtains a token, or fails with the server's stated reason.
     *
     * @throws IllegalStateException naming the reason the server gave, so a wrong
     *     password reads as "Bad credentials" rather than as a null token surfacing
     *     several calls later as a 403
     */
    public String requestToken(String username, String password) {
        ApiResponse<AuthResponse> response = authenticate(username, password);
        AuthResponse body = response.requireBody();

        if (!body.isSuccessful()) {
            throw new IllegalStateException(
                    "Authentication failed for user \"%s\": %s (HTTP %d)"
                            .formatted(username, body.reason(), response.statusCode()));
        }
        return body.token();
    }

    /** Obtains a token using the credentials in the environment. */
    public String requestToken() {
        return requestToken(Secrets.username(), Secrets.password());
    }

    /**
     * A per-thread token cache using the credentials in the environment.
     *
     * <p>Built here so callers do not have to wire the supplier themselves and
     * accidentally create one cache per client, which would defeat the caching.
     */
    public AuthTokenCache cache() {
        return new AuthTokenCache(this::requestToken);
    }

    /**
     * A per-thread token cache using the given credentials.
     *
     * <p>Exists because {@link #cache()} reads {@link Secrets}, which fails when the
     * environment is not configured. That is right for a suite and wrong for a caller
     * that already holds credentials: the framework's own tests run against a stub and
     * have no business requiring the real environment to be set up.
     */
    public AuthTokenCache cache(String username, String password) {
        return new AuthTokenCache(() -> requestToken(username, password));
    }
}
