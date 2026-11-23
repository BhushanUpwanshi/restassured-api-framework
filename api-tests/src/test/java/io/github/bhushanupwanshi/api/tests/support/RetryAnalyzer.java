package io.github.bhushanupwanshi.api.tests.support;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Re-runs a failed test once, but only if it is in the {@value #GROUP} group.
 *
 * <p>This is a test retry, and test retries hide product bugs, so it is opt-in per test
 * and limited to checks whose failure can come from a shared host's noise rather than
 * from the service. Timing checks against a free public dyno are the one case here. A
 * functional test that fails once and passes on retry is a flaky test to fix, not one to
 * re-run.
 *
 * <p>Transport retries (connection reset, 502/503/504) are a different thing and live in
 * {@code api-core}'s {@code Retries}. They re-send a request; this re-runs a test.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    public static final String GROUP = "third-party-timing";

    private static final Logger log = LoggerFactory.getLogger(RetryAnalyzer.class);
    private static final int MAX_RETRIES = 1;

    private int attempts;

    @Override
    public boolean retry(ITestResult result) {
        boolean eligible = Arrays.asList(result.getMethod().getGroups()).contains(GROUP);
        if (!eligible || attempts >= MAX_RETRIES) {
            return false;
        }
        attempts++;
        log.warn("retrying {} once: it is tagged {} and failed with: {}",
                result.getName(), GROUP, result.getThrowable() == null ? "?" : result.getThrowable().getMessage());
        return true;
    }
}
