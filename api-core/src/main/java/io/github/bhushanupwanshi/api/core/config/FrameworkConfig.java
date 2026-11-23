package io.github.bhushanupwanshi.api.core.config;

import java.time.Duration;

/**
 * Resolved settings for one run. Immutable, built once by {@link ConfigLoader}.
 *
 * <p>Nothing reads {@code System.getProperty} or {@code System.getenv} after this
 * exists. Config arrives as an argument, which is what keeps clients and flows
 * testable against a stub on a random port.
 *
 * @param environment     the environment these settings came from
 * @param baseUri         root URI of the service under test, no trailing slash
 * @param authPath        path that issues a token
 * @param connectTimeout  how long to wait for a connection
 * @param readTimeout     how long to wait for a response body
 * @param maxRetries      transport-level retry attempts after the first try
 * @param retryBackoff    base delay between transport retries; grows exponentially
 */
public record FrameworkConfig(
        Environment environment,
        String baseUri,
        String authPath,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Duration retryBackoff) {

    public FrameworkConfig {
        if (baseUri == null || baseUri.isBlank()) {
            throw new IllegalArgumentException("baseUri must not be blank");
        }
        if (baseUri.endsWith("/")) {
            // Caught here rather than tolerated, because a trailing slash produces
            // "//bookings" paths that some servers accept and others 404 on, and the
            // resulting failure points at the test rather than at the config.
            throw new IllegalArgumentException(
                    "baseUri must not end with '/': " + baseUri);
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
    }

    /** Absolute URI for a path, which may or may not start with a slash. */
    public String uri(String path) {
        return path.startsWith("/") ? baseUri + path : baseUri + "/" + path;
    }
}
