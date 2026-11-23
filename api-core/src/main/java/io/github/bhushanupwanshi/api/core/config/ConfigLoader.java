package io.github.bhushanupwanshi.api.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@link FrameworkConfig} from four sources, highest priority first:
 *
 * <ol>
 *   <li>system property, e.g. {@code -Dapi.baseUri=...}
 *   <li>environment variable, e.g. {@code API_BASE_URI}
 *   <li>{@code config-<env>.properties} on the classpath
 *   <li>{@code config-default.properties} on the classpath
 * </ol>
 *
 * <p>The order is the point: CI overrides a profile without editing a file, and a
 * developer overrides CI for one run without editing anything. The precedence is
 * logged at startup, because a suite that silently ran against the wrong base URI is
 * worse than one that failed to start.
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String DEFAULT_PROPERTIES = "config-default.properties";

    private ConfigLoader() {
    }

    /** Loads configuration for the environment selected by {@code api.env}. */
    public static FrameworkConfig load() {
        return load(Environment.parse(rawValue(Environment.KEY)));
    }

    /**
     * Loads configuration for a named environment.
     *
     * @param environment environment to resolve against
     * @return immutable resolved config
     */
    public static FrameworkConfig load(Environment environment) {
        Properties defaults = readProperties(DEFAULT_PROPERTIES, true);
        Properties profile = readProperties("config-%s.properties".formatted(environment.key()), false);

        FrameworkConfig config = new FrameworkConfig(
                environment,
                require("api.baseUri", profile, defaults),
                require("api.authPath", profile, defaults),
                Duration.ofMillis(requireLong("api.connectTimeoutMs", profile, defaults)),
                Duration.ofMillis(requireLong("api.readTimeoutMs", profile, defaults)),
                (int) requireLong("api.maxRetries", profile, defaults),
                Duration.ofMillis(requireLong("api.retryBackoffMs", profile, defaults)));

        log.info("config resolved: env={} baseUri={} readTimeout={}ms maxRetries={}",
                config.environment(), config.baseUri(), config.readTimeout().toMillis(), config.maxRetries());
        return config;
    }

    /**
     * Resolves one key through the precedence chain.
     *
     * @throws IllegalStateException naming the key and every place it was looked for
     */
    private static String require(String key, Properties profile, Properties defaults) {
        String value = resolve(key, profile, defaults);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    ("Missing configuration \"%s\". Set it as a system property (-D%s=...), "
                            + "as the environment variable %s, or in config-default.properties.")
                            .formatted(key, key, envVarName(key)));
        }
        return value.trim();
    }

    private static long requireLong(String key, Properties profile, Properties defaults) {
        String value = require(key, profile, defaults);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Configuration \"%s\" must be a number, found \"%s\"".formatted(key, value), e);
        }
    }

    private static String resolve(String key, Properties profile, Properties defaults) {
        String direct = rawValue(key);
        if (direct != null) {
            return direct;
        }
        String fromProfile = profile.getProperty(key);
        return fromProfile != null ? fromProfile : defaults.getProperty(key);
    }

    /** System property, then environment variable. */
    private static String rawValue(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(envVarName(key));
        return env != null && !env.isBlank() ? env : null;
    }

    /** {@code api.baseUri} becomes {@code API_BASE_URI}. */
    static String envVarName(String key) {
        StringBuilder out = new StringBuilder(key.length() + 4);
        for (char c : key.toCharArray()) {
            if (c == '.' || c == '-') {
                out.append('_');
            } else if (Character.isUpperCase(c)) {
                out.append('_').append(c);
            } else {
                out.append(Character.toUpperCase(c));
            }
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    private static Properties readProperties(String resource, boolean required) {
        Properties properties = new Properties();
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException(
                            "Required resource %s is not on the classpath".formatted(resource));
                }
                log.debug("no {} on the classpath; falling back to defaults", resource);
                return properties;
            }
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }
}
