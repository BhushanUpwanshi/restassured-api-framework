package io.github.bhushanupwanshi.api.core.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

/**
 * The token cache, without HTTP.
 *
 * <p>The issuer is injected, so these tests count how many tokens were actually issued.
 * That count is the whole point: caching that silently logs in on every call would pass
 * any test that only checked the returned value.
 */
public class AuthTokenCacheTest {

    /** An issuer handing out token-1, token-2, and so on, counting as it goes. */
    private static final class CountingIssuer implements java.util.function.Supplier<String> {
        private final AtomicInteger issued = new AtomicInteger();

        @Override
        public String get() {
            return "token-" + issued.incrementAndGet();
        }

        int count() {
            return issued.get();
        }
    }

    @Test
    public void issuesATokenOnFirstUse() {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        assertThat(cache.isCached()).isFalse();
        assertThat(cache.token()).isEqualTo("token-1");
        assertThat(cache.isCached()).isTrue();
    }

    @Test
    public void reusesTheTokenAcrossCalls() {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        cache.token();
        cache.token();
        cache.token();

        // Three calls, one login. Without this the suite logs in once per request,
        // which is slow and hammers the auth endpoint for no reason.
        assertThat(issuer.count()).isEqualTo(1);
    }

    @Test
    public void issuesAFreshTokenAfterInvalidation() {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        assertThat(cache.token()).isEqualTo("token-1");
        cache.invalidate();

        assertThat(cache.isCached()).isFalse();
        assertThat(cache.token()).isEqualTo("token-2");
        assertThat(issuer.count()).isEqualTo(2);
    }

    @Test
    public void invalidatingWithoutATokenIsHarmless() {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        cache.invalidate();
        cache.invalidate();

        assertThat(issuer.count()).isZero();
    }

    /**
     * The reason this class exists.
     *
     * <p>A {@code static String token} passes every test above and fails only here, and
     * only sometimes. Each thread must hold its own token; one thread seeing another's is
     * the bug that presents as an intermittent 403 and reproduces for nobody.
     */
    @Test
    public void givesEachThreadItsOwnToken() throws Exception {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        int threads = 8;
        List<String> seen = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    // Twice, so a thread that leaked another's token would show up as a
                    // mismatch between its own two reads as well.
                    String first = cache.token();
                    String second = cache.token();
                    if (first.equals(second)) {
                        seen.add(first);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "token-thread-" + i).start();
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown(); // release them together, to make overlap likely
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(seen).hasSize(threads);
        assertThat(Set.copyOf(seen)).hasSize(threads);
        assertThat(issuer.count()).isEqualTo(threads);
    }

    @Test
    public void oneThreadInvalidatingDoesNotDisturbAnother() throws Exception {
        CountingIssuer issuer = new CountingIssuer();
        AuthTokenCache cache = new AuthTokenCache(issuer);

        String mine = cache.token();

        Thread other = new Thread(() -> {
            cache.token();
            cache.invalidate();
        });
        other.start();
        other.join(5_000);

        assertThat(cache.token()).isEqualTo(mine);
    }

    @Test
    public void failsClearlyWhenTheIssuerReturnsNothing() {
        AuthTokenCache blank = new AuthTokenCache(() -> "  ");

        assertThatThrownBy(blank::token)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_USERNAME");

        AuthTokenCache none = new AuthTokenCache(() -> null);
        assertThatThrownBy(none::token).isInstanceOf(IllegalStateException.class);
    }
}
