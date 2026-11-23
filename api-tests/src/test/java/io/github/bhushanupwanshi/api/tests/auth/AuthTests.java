package io.github.bhushanupwanshi.api.tests.auth;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bhushanupwanshi.api.core.config.Secrets;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/**
 * Login, including the parts that are easy to get wrong.
 *
 * <p>The notable one: a rejected login is HTTP <b>200</b> with {@code {"reason":"Bad
 * credentials"}}. A suite that checked the status would count every failed login as a
 * success. These tests pin that, so the client's handling of it is tested against the
 * real thing and not only against a stub.
 */
public class AuthTests extends BaseApiTest {

    @Test(groups = {"regression", "smoke"})
    public void validCredentialsGetAToken() {
        var response = api().auth().authenticate(Secrets.username(), Secrets.password());

        assertThat(response).hasStatus(200);
        assertThat(response.requireBody().isSuccessful()).isTrue();
        assertThat(response.requireBody().token()).isNotBlank();
    }

    @Test(groups = {"regression", "negative"})
    public void badCredentialsAre200WithAReasonNotA401() {
        var response = api().auth().authenticate(Secrets.username(), "definitely-wrong");

        assertThat(response).hasStatus(200);
        assertThat(response.requireBody().isSuccessful()).isFalse();
        assertThat(response.requireBody().reason()).isEqualTo("Bad credentials");
    }

    @Test(groups = {"regression", "negative"})
    public void aFailedLoginSaysWhyInsteadOfReturningANullToken() {
        assertThatThrownBy(() -> api().auth().requestToken("nobody", "nothing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bad credentials");
    }

    @Test(groups = "regression")
    public void parallelLoginsGetIndependentTokens() throws Exception {
        int threads = 4;
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    tokens.add(api().auth().requestToken());
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("logins finished").isTrue();
        } finally {
            pool.shutdownNow();
        }

        // The service issues a fresh token per login, so four logins are four tokens. A
        // shared static token would collapse these, and that is the bug this guards.
        assertThat(tokens).hasSize(threads);
    }
}
