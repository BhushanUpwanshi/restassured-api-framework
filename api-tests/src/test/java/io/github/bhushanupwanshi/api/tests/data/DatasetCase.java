package io.github.bhushanupwanshi.api.tests.data;

import java.util.List;

/**
 * What every row in every dataset has, whatever else it carries.
 *
 * <p>{@link Datasets} filters on these and {@link DatasetContractTest} validates them, so
 * a new kind of dataset gets both for free by implementing this.
 */
public interface DatasetCase {

    String id();

    String description();

    /** Environment keys this case runs in ({@code dev}, {@code qa}, {@code prod}). Empty means all. */
    List<String> environments();

    List<String> tags();

    Boolean enabled();

    default boolean isEnabled() {
        return enabled() == null || enabled();
    }

    default boolean runsIn(String environment) {
        return environments() == null || environments().isEmpty() || environments().contains(environment);
    }

    /** How the case appears in reports: {@code BV-01 rejects a booking with no firstname}. */
    default String displayName() {
        return id() + " " + description();
    }
}
