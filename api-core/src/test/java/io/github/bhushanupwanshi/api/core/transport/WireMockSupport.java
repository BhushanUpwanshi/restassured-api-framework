package io.github.bhushanupwanshi.api.core.transport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.bhushanupwanshi.api.core.config.Environment;
import io.github.bhushanupwanshi.api.core.config.FrameworkConfig;
import java.time.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

/**
 * A stub server for the framework's own tests.
 *
 * <p>These tests are about the framework, not about anybody's API, so they must not
 * reach the network. A stub also does what no real service will do on request: answer
 * 503 twice and then 200, reset a connection, sit silent past a timeout, return a
 * malformed body. Those are exactly the paths the transport layer exists for, and they
 * are untestable against a live host.
 *
 * <p>Same reasoning as the fixture-origin interception in the Playwright framework.
 *
 * <p>Port 0 means the OS picks a free one, so two test classes running in parallel never
 * collide on a hardcoded port.
 */
public abstract class WireMockSupport {

    protected WireMockServer wireMock;

    @BeforeClass(alwaysRun = true)
    public void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterMethod(alwaysRun = true)
    public void resetStubs() {
        // Per method, so one test's stubs and request journal cannot satisfy or
        // confuse the next one.
        wireMock.resetAll();
        CorrelationIdFilter.clear();
    }

    @AfterClass(alwaysRun = true)
    public void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    /** Base URI of the running stub. */
    protected String baseUri() {
        return "http://localhost:" + wireMock.port();
    }

    /** Config pointing at the stub, with retrying disabled. */
    protected FrameworkConfig configWithoutRetry() {
        return config(0, Duration.ZERO);
    }

    /** Config pointing at the stub, with the given retry budget. */
    protected FrameworkConfig config(int maxRetries, Duration backoff) {
        return new FrameworkConfig(
                Environment.DEV,
                baseUri(),
                "/auth",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                maxRetries,
                backoff);
    }
}
