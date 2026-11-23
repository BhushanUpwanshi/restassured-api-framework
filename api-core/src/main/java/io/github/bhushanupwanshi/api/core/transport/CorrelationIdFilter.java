package io.github.bhushanupwanshi.api.core.transport;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.UUID;

/**
 * Stamps every request with {@code X-Correlation-Id} and remembers the last one.
 *
 * <p>Four lines of work for the thing that makes a failure against a real service
 * findable: the id in the test output is the id in the server's logs. Without it a
 * failed assertion against a shared environment means searching by timestamp and
 * hoping no other suite was running.
 *
 * <p>The id is held in a {@link ThreadLocal} because the suite runs in parallel, and a
 * shared field would hand one thread another thread's id, which is worse than having
 * none at all.
 */
public final class CorrelationIdFilter implements OrderedFilter {

    /** Header carrying the id. */
    public static final String HEADER = "X-Correlation-Id";

    /**
     * Runs inside the retry filter, so each attempt gets its own id.
     *
     * <p>Deliberate: the point of the id is to locate one request in a server log, and
     * three retried attempts are three requests there.
     */
    public static final int ORDER = 500;

    private static final ThreadLocal<String> LAST_ID = new ThreadLocal<>();

    @Override
    public Response filter(
            FilterableRequestSpecification requestSpec,
            FilterableResponseSpecification responseSpec,
            FilterContext ctx) {

        String id = UUID.randomUUID().toString();
        LAST_ID.set(id);
        requestSpec.header(HEADER, id);

        return ctx.next(requestSpec, responseSpec);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** The id of the most recent request on this thread, if there has been one. */
    public static String lastId() {
        return LAST_ID.get();
    }

    /** Clears the id. Called between tests so one test cannot report another's id. */
    public static void clear() {
        LAST_ID.remove();
    }
}
