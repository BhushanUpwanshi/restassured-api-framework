package io.github.bhushanupwanshi.api.core.transport;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One SLF4J line per call: method, URI, status, time, correlation id.
 *
 * <p>REST Assured already has {@code RequestLoggingFilter} and
 * {@code ResponseLoggingFilter}, and they are not used here. They write a full dump of
 * headers and bodies to a {@code PrintStream}, which produces a wall of text per call
 * and bypasses the logging framework entirely, so there is no level to turn it down to.
 *
 * <p>The split instead: a scannable line here, and the full request and response
 * attached to the Allure result by {@code AllureRestAssured} — also not rebuilt. Reading
 * a CI log is a different job from investigating one failure, and the same output serves
 * neither well.
 */
public final class HttpLogFilter implements OrderedFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLogFilter.class);

    /** Innermost of the framework's filters, so it times only the request itself. */
    public static final int ORDER = 900;

    @Override
    public Response filter(
            FilterableRequestSpecification requestSpec,
            FilterableResponseSpecification responseSpec,
            FilterContext ctx) {

        Response response = ctx.next(requestSpec, responseSpec);

        String correlationId = requestSpec.getHeaders().getValue(CorrelationIdFilter.HEADER);
        log.debug("{} {} -> {} in {}ms [{}]",
                requestSpec.getMethod(),
                requestSpec.getURI(),
                response.getStatusCode(),
                response.timeIn(TimeUnit.MILLISECONDS),
                correlationId == null ? "no-correlation-id" : correlationId);

        return response;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
