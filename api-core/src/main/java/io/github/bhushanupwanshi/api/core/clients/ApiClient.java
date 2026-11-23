package io.github.bhushanupwanshi.api.core.clients;

import io.github.bhushanupwanshi.api.core.models.error.ApiError;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.core.transport.Retries;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.function.Supplier;

/**
 * Base for clients that do not need a token.
 *
 * <p>Owns the three things every call shares: the shared specification, the retry loop,
 * and turning a raw response into a typed {@link ApiResponse}. Tests never touch a
 * mapper, never build a specification, and never write a retry loop, which is the whole
 * reason this layer exists.
 *
 * <p>Retrying lives here rather than in a filter because REST Assured's filter chain is
 * single-use; see {@link Retries}.
 */
public abstract class ApiClient {

    protected final Specs specs;

    protected ApiClient(Specs specs) {
        this.specs = specs;
    }

    /** A request built from the shared specification. */
    protected RequestSpecification request() {
        return RestAssured.given().spec(specs.request());
    }

    /**
     * Sends one call, retrying per the configured policy.
     *
     * @param description what is being called, used in retry log lines
     * @param call        performs a single attempt
     */
    protected Response send(String description, Supplier<Response> call) {
        return Retries.execute(specs.retryPolicy(), description, call);
    }

    /**
     * Binds a response body to a type, for successful responses only.
     *
     * <p>A 4xx or 5xx body is <strong>not</strong> deserialized. This API answers errors
     * with plain text — {@code Not Found}, {@code Forbidden}, {@code Internal Server
     * Error} — so attempting to bind one throws, and the caller gets a deserialization
     * failure in place of the 404 it was about to assert on. Tolerating that in
     * {@link io.github.bhushanupwanshi.api.core.models.error.ApiError} was not enough: the
     * binding path has to be tolerant too, or the error never reaches the error model.
     *
     * <p>A failure therefore arrives as {@code body == null} with {@code rawBody} intact
     * and the status set, which is what a negative test asserts on anyway.
     *
     * <p>On a 2xx the binding is strict and a failure propagates, because a success that
     * does not match the model is a real contract break and should be loud.
     */
    protected <T> ApiResponse<T> bind(Response response, Class<T> type) {
        if (response.getStatusCode() >= 400) {
            return ApiResponse.from(response, raw -> null);
        }
        return ApiResponse.from(response, raw -> Json.read(raw, type));
    }

    /** Wraps a response whose body is not needed. */
    protected ApiResponse<Void> noBody(Response response) {
        return ApiResponse.from(response);
    }

    /**
     * Reads a failure out of a response.
     *
     * <p>Tolerates a non-JSON body, which this API returns for 403 and 404.
     */
    protected ApiError errorFrom(Response response) {
        return ApiError.from(
                response.getStatusCode(),
                response.getBody() == null ? "" : response.getBody().asString());
    }
}
