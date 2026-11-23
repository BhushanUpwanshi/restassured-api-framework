package io.github.bhushanupwanshi.api.tests.support;

import io.github.bhushanupwanshi.api.core.ApiFramework;

/**
 * The one framework instance a test JVM uses.
 *
 * <p>TestNG classes and Cucumber steps share it, so they share one config and one token
 * cache. The cache is per thread inside, so one instance is safe under parallel runs and
 * keeps logins to one per thread instead of one per test class.
 */
public final class Api {

    private Api() {
    }

    private static final class Holder {
        private static final ApiFramework INSTANCE = ApiFramework.fromEnvironment();
    }

    public static ApiFramework framework() {
        return Holder.INSTANCE;
    }
}
