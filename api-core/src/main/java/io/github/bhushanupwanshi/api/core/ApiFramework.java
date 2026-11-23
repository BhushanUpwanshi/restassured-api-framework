package io.github.bhushanupwanshi.api.core;

import io.github.bhushanupwanshi.api.core.clients.AuthClient;
import io.github.bhushanupwanshi.api.core.clients.BookingClient;
import io.github.bhushanupwanshi.api.core.config.ConfigLoader;
import io.github.bhushanupwanshi.api.core.config.FrameworkConfig;
import io.github.bhushanupwanshi.api.core.flows.BookingFlow;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;

/**
 * The one place that wires the layers together.
 *
 * <p>Tests and step definitions are not allowed to build a {@link Specs} or an
 * {@link AuthTokenCache}: those are transport, and the layering rules keep test code out
 * of transport. Something still has to construct them, and it is this class. A consumer
 * asks for a client or a flow and gets one already wired to the same config, the same
 * specification and the same token cache.
 *
 * <p>One token cache per framework instance, shared by every client it hands out. The
 * cache is per thread inside, so sharing it across a parallel suite is safe and is what
 * keeps logins down to one per thread.
 */
public final class ApiFramework {

    private final FrameworkConfig config;
    private final Specs specs;
    private final AuthClient authClient;
    private final AuthTokenCache tokens;

    private ApiFramework(FrameworkConfig config, Specs specs, AuthTokenCache tokens) {
        this.config = config;
        this.specs = specs;
        this.authClient = new AuthClient(specs);
        this.tokens = tokens;
    }

    /** Resolved config, credentials from the environment, Allure reporting on. */
    public static ApiFramework fromEnvironment() {
        FrameworkConfig config = ConfigLoader.load();
        Specs specs = new Specs(config, true);
        return new ApiFramework(config, specs, new AuthClient(specs).cache());
    }

    /**
     * The given config and credentials, Allure reporting as chosen.
     *
     * <p>For callers that already hold credentials, and for the framework's own tests,
     * which run against a stub and must not require the real environment.
     */
    public static ApiFramework of(FrameworkConfig config, String username, String password,
                                  boolean reportToAllure) {
        Specs specs = new Specs(config, reportToAllure);
        return new ApiFramework(config, specs, new AuthClient(specs).cache(username, password));
    }

    public FrameworkConfig config() {
        return config;
    }

    public AuthClient auth() {
        return authClient;
    }

    public BookingClient bookings() {
        return new BookingClient(specs, tokens);
    }

    public BookingFlow bookingFlow() {
        return new BookingFlow(bookings());
    }

    /** Drops this thread's cached token, for tests that need a fresh login. */
    public void forgetToken() {
        tokens.invalidate();
    }
}
