package io.github.bhushanupwanshi.api.core.transport;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds one auth token per thread, created on first use.
 *
 * <p><strong>Why per thread.</strong> The suite runs with
 * {@code parallel="methods"}, and the API issues a separate token per login. A
 * {@code static String token} works perfectly until parallelism is switched on, and then
 * fails intermittently: one thread's login overwrites another's token mid-request, and
 * the resulting 403 looks like a server fault rather than a framework one. That is among
 * the hardest failures to trace back, because the suite passes when run one test at a
 * time, which is exactly how someone will try to reproduce it.
 *
 * <p>The token source is injected rather than built here, so the cache can be tested
 * without HTTP and the test can count how many times a token was actually issued.
 */
public final class AuthTokenCache {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenCache.class);

    private final ThreadLocal<String> token = new ThreadLocal<>();
    private final Supplier<String> issuer;

    /**
     * @param issuer obtains a fresh token; called on first use and after an invalidation
     */
    public AuthTokenCache(Supplier<String> issuer) {
        this.issuer = issuer;
    }

    /**
     * This thread's token, obtaining one if it has none.
     *
     * @return a token, never {@code null}
     * @throws IllegalStateException when the issuer returns nothing
     */
    public String token() {
        String cached = token.get();
        if (cached != null) {
            return cached;
        }

        String issued = issuer.get();
        if (issued == null || issued.isBlank()) {
            throw new IllegalStateException(
                    "Token issuer returned no token. Check API_USERNAME and API_PASSWORD.");
        }

        token.set(issued);
        log.debug("issued a token for thread {}", Thread.currentThread().getName());
        return issued;
    }

    /**
     * Discards this thread's token, so the next {@link #token()} obtains a fresh one.
     *
     * <p>Called when a request comes back as an auth failure. The point is that an
     * expired token self-heals once, rather than failing every remaining test in the
     * suite with the same 403.
     */
    public void invalidate() {
        if (token.get() != null) {
            log.debug("invalidating the token for thread {}", Thread.currentThread().getName());
        }
        // remove(), not set(null): leaving the entry in place keeps a reference on a
        // pooled thread for the life of the JVM.
        token.remove();
    }

    /** Whether this thread currently holds a token. Mainly for assertions. */
    public boolean isCached() {
        return token.get() != null;
    }
}
