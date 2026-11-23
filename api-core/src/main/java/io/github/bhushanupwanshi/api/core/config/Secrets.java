package io.github.bhushanupwanshi.api.core.config;

/**
 * Reads credentials from the environment, and only from the environment.
 *
 * <p>There is deliberately no fallback value and no properties file. A default would
 * make the suite runnable out of the box, which sounds helpful and teaches the wrong
 * habit: the next credential added this way is a real one, in a public repository.
 *
 * <p>The failure is also deliberate. An unset credential that silently becomes an
 * empty string produces a 401 or 403 from the server, and the next hour is spent
 * reading the wrong logs. Failing at startup with the variable's name costs seconds.
 *
 * <p>The target API publishes its demo credentials, so the README states them and CI
 * sets them as plain environment variables rather than repository secrets. That they
 * are not sensitive does not change how they are read.
 */
public final class Secrets {

    /** Environment variable holding the API username. */
    public static final String USERNAME = "API_USERNAME";

    /** Environment variable holding the API password. */
    public static final String PASSWORD = "API_PASSWORD";

    private Secrets() {
    }

    /** The configured username. */
    public static String username() {
        return require(USERNAME);
    }

    /** The configured password. */
    public static String password() {
        return require(PASSWORD);
    }

    /**
     * Reads a required environment variable.
     *
     * @param name variable to read
     * @return its value, trimmed
     * @throws IllegalStateException naming the variable and how to set it
     */
    public static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    ("Environment variable %s is not set. The suite needs it to authenticate. "
                            + "On Windows: $env:%s = \"...\"  On Linux/macOS: export %s=...")
                            .formatted(name, name, name));
        }
        return value.trim();
    }

    /**
     * Whether both credentials are present.
     *
     * <p>Lets a suite skip authenticated tests with a clear reason instead of failing
     * every one of them with the same stack trace.
     */
    public static boolean areConfigured() {
        return isSet(USERNAME) && isSet(PASSWORD);
    }

    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
