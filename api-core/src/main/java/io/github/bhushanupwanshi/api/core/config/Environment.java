package io.github.bhushanupwanshi.api.core.config;

import java.util.Arrays;
import java.util.Locale;

/**
 * The environments the suite can run against.
 *
 * <p>An enum rather than a string so a typo is a compile error or a named failure at
 * startup, instead of a silent fall-through to whichever environment the defaults
 * happen to point at.
 */
public enum Environment {
    DEV,
    QA,
    PROD;

    /** Property and environment-variable key that selects the environment. */
    public static final String KEY = "api.env";

    /** Used when nothing selects an environment. */
    public static final Environment DEFAULT = DEV;

    /**
     * Parses an environment name, case-insensitively.
     *
     * @param raw name to parse; blank or {@code null} yields {@link #DEFAULT}
     * @return the matching environment
     * @throws IllegalArgumentException naming the valid values, when {@code raw} matches none
     */
    public static Environment parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(env -> env.name().equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown %s \"%s\". Expected one of: %s"
                                .formatted(KEY, raw, Arrays.toString(values()))));
    }

    /** Lower-case name, as used in resource paths such as {@code config-qa.properties}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
