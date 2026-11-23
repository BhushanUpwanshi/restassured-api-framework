package io.github.bhushanupwanshi.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Covers the precedence chain and the failure messages.
 *
 * <p>Environment variables cannot be set from inside a JVM, so the env-var tier is
 * exercised through {@link ConfigLoader#envVarName} (the mapping) plus the system
 * property tier (the lookup). The combination is what the chain is made of; a test
 * that spawned a subprocess to set a real variable would cover the same two things
 * and take a hundred times longer.
 */
public class ConfigLoaderTest {

    private static final String BASE_URI_KEY = "api.baseUri";

    @AfterMethod(alwaysRun = true)
    public void clearOverrides() {
        // Properties are process-global. Left behind, one test silently configures
        // the next, which is the kind of failure that only shows up when the suite
        // runs in a different order.
        System.clearProperty(BASE_URI_KEY);
        System.clearProperty(Environment.KEY);
    }

    @Test
    public void readsBaseUriFromTheDefaultPropertiesFile() {
        FrameworkConfig config = ConfigLoader.load(Environment.DEV);

        assertThat(config.baseUri()).isEqualTo("https://restful-booker.herokuapp.com");
        assertThat(config.authPath()).isEqualTo("/auth");
        assertThat(config.environment()).isEqualTo(Environment.DEV);
    }

    @Test
    public void profileFileOverridesTheDefaultFile() {
        FrameworkConfig dev = ConfigLoader.load(Environment.DEV);
        FrameworkConfig qa = ConfigLoader.load(Environment.QA);

        // config-qa.properties raises both of these; everything else is inherited.
        assertThat(dev.readTimeout().toMillis()).isEqualTo(15_000);
        assertThat(qa.readTimeout().toMillis()).isEqualTo(20_000);
        assertThat(qa.maxRetries()).isEqualTo(3);
        assertThat(qa.baseUri()).isEqualTo(dev.baseUri());
    }

    @Test
    public void systemPropertyBeatsBothFiles() {
        System.setProperty(BASE_URI_KEY, "https://override.example.com");

        assertThat(ConfigLoader.load(Environment.QA).baseUri())
                .isEqualTo("https://override.example.com");
    }

    @Test
    public void environmentIsSelectedByApiEnvProperty() {
        System.setProperty(Environment.KEY, "qa");

        assertThat(ConfigLoader.load().environment()).isEqualTo(Environment.QA);
    }

    @Test
    public void environmentDefaultsWhenNothingSelectsOne() {
        assertThat(ConfigLoader.load().environment()).isEqualTo(Environment.DEFAULT);
    }

    @Test
    public void unknownEnvironmentFailsAndListsTheValidOnes() {
        assertThatThrownBy(() -> Environment.parse("staging"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("staging")
                .hasMessageContaining("DEV")
                .hasMessageContaining("QA")
                .hasMessageContaining("PROD");
    }

    /**
     * A blank override is ignored rather than treated as a value.
     *
     * <p>Worth pinning down, because the alternative is worse in a specific way: CI
     * systems pass through unset variables as empty strings, so a tier that accepted
     * blank would resolve {@code baseUri} to {@code ""} and the suite would fail
     * against nothing rather than falling back to a working default.
     */
    @Test
    public void blankOverrideIsIgnoredAndTheFileStillWins() {
        System.setProperty(BASE_URI_KEY, "   ");

        assertThat(ConfigLoader.load(Environment.DEV).baseUri())
                .isEqualTo("https://restful-booker.herokuapp.com");
    }

    @Test
    public void missingSecretNamesTheVariableAndHowToSetIt() {
        assertThatThrownBy(() -> Secrets.require("API_DEFINITELY_NOT_SET_12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_DEFINITELY_NOT_SET_12345")
                .hasMessageContaining("export");
    }

    @Test
    public void nonNumericTimeoutFailsWithTheOffendingValue() {
        System.setProperty("api.readTimeoutMs", "soon");
        try {
            assertThatThrownBy(() -> ConfigLoader.load(Environment.DEV))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("api.readTimeoutMs")
                    .hasMessageContaining("soon");
        } finally {
            System.clearProperty("api.readTimeoutMs");
        }
    }

    @Test
    public void mapsPropertyKeysToEnvironmentVariableNames() {
        assertThat(ConfigLoader.envVarName("api.baseUri")).isEqualTo("API_BASE_URI");
        assertThat(ConfigLoader.envVarName("api.readTimeoutMs")).isEqualTo("API_READ_TIMEOUT_MS");
        assertThat(ConfigLoader.envVarName("api.env")).isEqualTo("API_ENV");
    }

    @Test
    public void rejectsABaseUriWithATrailingSlash() {
        assertThatThrownBy(() -> new FrameworkConfig(
                        Environment.DEV,
                        "https://example.com/",
                        "/auth",
                        java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofSeconds(15),
                        2,
                        java.time.Duration.ofMillis(400)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not end with")
                .hasMessageContaining("https://example.com/");
    }

    @Test
    public void buildsAbsoluteUrisWithOrWithoutALeadingSlash() {
        FrameworkConfig config = ConfigLoader.load(Environment.DEV);

        assertThat(config.uri("/booking")).isEqualTo(config.baseUri() + "/booking");
        assertThat(config.uri("booking")).isEqualTo(config.baseUri() + "/booking");
    }
}
