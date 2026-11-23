package io.github.bhushanupwanshi.api.core.clients;

import io.github.bhushanupwanshi.api.core.models.error.ApiError;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.restassured.response.Response;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for clients whose calls require a token.
 *
 * <p>Separate from {@link ApiClient} rather than one class with a nullable token cache:
 * {@code AuthClient} is the thing that obtains tokens, so it cannot need one, and a
 * single base would have handed it a {@code null} to guard against on every call.
 *
 * <p>The token travels as {@code Cookie: token=...}. Verified against restful-booker on
 * 2026-10-06, along with the fact that {@code Authorization: Bearer} is rejected with 403
 * — worth noting because Bearer is the obvious guess and it silently does not work.
 */
public abstract class AuthenticatedClient extends ApiClient {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedClient.class);

    /** Cookie name the API expects the token under. */
    protected static final String TOKEN_COOKIE = "token";

    protected final AuthTokenCache tokens;

    protected AuthenticatedClient(Specs specs, AuthTokenCache tokens) {
        super(specs);
        this.tokens = tokens;
    }

    /**
     * Sends an authenticated call, refreshing the token once if it is rejected.
     *
     * <p>Exactly one refresh, never a loop. If a fresh token is also refused the second
     * response is returned as-is, so the test reports the real 403 rather than hanging in
     * a retry cycle or reporting a framework error. The usual mistake here is recursion,
     * which turns one expired token into an infinite login storm against the service.
     *
     * @param description what is being called, for log lines
     * @param call        performs one attempt with the token it is given
     */
    protected Response sendAuthenticated(String description, Function<String, Response> call) {
        Response first = send(description, () -> call.apply(tokens.token()));

        ApiError error = errorFrom(first);
        if (!error.isAuthFailure()) {
            return first;
        }

        log.info("{} was refused with {}; refreshing the token and retrying once",
                description, first.getStatusCode());
        tokens.invalidate();

        return send(description + " (after token refresh)", () -> call.apply(tokens.token()));
    }
}
