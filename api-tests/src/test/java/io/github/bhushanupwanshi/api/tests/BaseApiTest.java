package io.github.bhushanupwanshi.api.tests;

import io.github.bhushanupwanshi.api.core.ApiFramework;
import io.github.bhushanupwanshi.api.core.clients.BookingClient;
import io.github.bhushanupwanshi.api.core.data.CreatedResourceRegistry;
import io.github.bhushanupwanshi.api.core.flows.BookingFlow;
import io.github.bhushanupwanshi.api.tests.data.DatasetCase;
import io.github.bhushanupwanshi.api.tests.support.Api;
import io.qameta.allure.Allure;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;

/**
 * What every suite class shares: the framework, and cleanup after each test.
 *
 * <p>Cleanup runs in {@code @AfterMethod}, on the same thread as the test, so it deletes
 * exactly what that test created. The registry is per thread, which is what makes this
 * correct under {@code parallel="methods"}: one test's teardown cannot delete another
 * test's live booking.
 */
public abstract class BaseApiTest {

    private static final Logger log = LoggerFactory.getLogger(BaseApiTest.class);

    protected ApiFramework api() {
        return Api.framework();
    }

    protected BookingClient bookings() {
        return api().bookings();
    }

    protected BookingFlow bookingFlow() {
        return api().bookingFlow();
    }

    /** Registers a booking the test created outside a flow, so teardown deletes it. */
    protected void cleanUpLater(int bookingId) {
        CreatedResourceRegistry.record("booking", bookingId, id -> bookings().delete(id));
    }

    @AfterMethod(alwaysRun = true)
    public void deleteWhatThisTestCreated() {
        List<String> cleaned = CreatedResourceRegistry.cleanUp();
        if (!cleaned.isEmpty()) {
            log.debug("cleaned up {}", cleaned);
        }
    }

    /**
     * Names the report entry after the case, so a failure in CI reads
     * {@code BV-01 rejects a booking with no firstname} rather than {@code rejects[0]}.
     */
    protected void nameCase(DatasetCase testCase) {
        Allure.getLifecycle().updateTestCase(result -> result.setName(testCase.displayName()));
        if (testCase.tags() != null) {
            testCase.tags().forEach(tag -> Allure.label("tag", tag));
        }
        log.info("case {}", testCase.displayName());
    }
}
