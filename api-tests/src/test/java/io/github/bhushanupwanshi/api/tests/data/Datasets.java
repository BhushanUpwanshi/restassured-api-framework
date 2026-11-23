package io.github.bhushanupwanshi.api.tests.data;

import com.fasterxml.jackson.databind.JavaType;
import io.github.bhushanupwanshi.api.core.config.Environment;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a dataset for the active environment.
 *
 * <p>Resolves {@code testdata/{env}/{name}.json}, falling back to
 * {@code testdata/default/{name}.json}. An environment file <em>replaces</em> the default
 * one; it is not merged into it. A merged dataset reads fine in a diff and then nobody can
 * say what actually ran. A whole-file override can be read in one place.
 *
 * <p>Cases that are disabled or not meant for this environment are counted and logged,
 * never dropped quietly:
 * {@code booking-validation: 14 cases, 12 run, 2 skipped for env=prod}. Coverage that
 * shrinks without a log line is coverage nobody notices losing.
 */
public final class Datasets {

    private static final Logger log = LoggerFactory.getLogger(Datasets.class);

    private Datasets() {
    }

    /** The environment the suite is running against, from {@code -Dapi.env}. */
    public static String activeEnvironment() {
        return Environment.parse(System.getProperty(Environment.KEY)).key();
    }

    /** Every case in the file that resolves for the active environment, unfiltered. */
    public static <T extends DatasetCase> TestCaseFile<T> file(String name, Class<T> caseType) {
        return file(name, caseType, activeEnvironment());
    }

    /** As {@link #file(String, Class)}, for an explicit environment. */
    public static <T extends DatasetCase> TestCaseFile<T> file(String name, Class<T> caseType, String env) {
        String path = resolve(name, env);
        JavaType type = Json.mapper().getTypeFactory().constructParametricType(TestCaseFile.class, caseType);
        try (InputStream in = Datasets.class.getClassLoader().getResourceAsStream(path)) {
            return Json.mapper().readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read dataset " + path + ": " + e.getMessage(), e);
        }
    }

    /** The cases that should run here, filtered and logged. */
    public static <T extends DatasetCase> List<T> cases(String name, Class<T> caseType) {
        String env = activeEnvironment();
        List<T> all = file(name, caseType, env).cases();
        List<T> running = all.stream().filter(c -> c.isEnabled() && c.runsIn(env)).toList();

        int skipped = all.size() - running.size();
        log.info("{}: {} cases, {} run, {} skipped for env={}", name, all.size(), running.size(), skipped, env);
        all.stream().filter(c -> !running.contains(c))
                .forEach(c -> log.info("  skipped {} ({})", c.id(), c.isEnabled() ? "not for " + env : "disabled"));
        return running;
    }

    /** {@link #cases} shaped for a TestNG {@code @DataProvider}. */
    public static <T extends DatasetCase> Object[][] rows(String name, Class<T> caseType) {
        return cases(name, caseType).stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    /** The classpath path the dataset resolves to for an environment. */
    static String resolve(String name, String env) {
        String specific = "testdata/%s/%s.json".formatted(env, name);
        if (Datasets.class.getClassLoader().getResource(specific) != null) {
            return specific;
        }
        String fallback = "testdata/default/%s.json".formatted(name);
        if (Datasets.class.getClassLoader().getResource(fallback) == null) {
            throw new IllegalStateException(
                    "No dataset \"%s\": neither %s nor %s is on the classpath".formatted(name, specific, fallback));
        }
        return fallback;
    }
}
