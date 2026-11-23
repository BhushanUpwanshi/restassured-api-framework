package io.github.bhushanupwanshi.api.core.clients;

import io.github.bhushanupwanshi.api.core.models.request.BookingRequest;
import io.github.bhushanupwanshi.api.core.models.response.Booking;
import io.github.bhushanupwanshi.api.core.models.response.BookingId;
import io.github.bhushanupwanshi.api.core.models.response.CreatedBooking;
import io.github.bhushanupwanshi.api.core.serialization.Json;
import io.github.bhushanupwanshi.api.core.transport.ApiResponse;
import io.github.bhushanupwanshi.api.core.transport.AuthTokenCache;
import io.github.bhushanupwanshi.api.core.transport.Specs;
import io.restassured.response.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Calls the booking endpoints.
 *
 * <p>Every status and body shape below was observed against restful-booker on 2026-10-06
 * rather than assumed, and several are not what the obvious guess would be:
 *
 * <table border="1">
 *   <caption>Observed behaviour</caption>
 *   <tr><th>Call</th><th>Status</th><th>Body</th></tr>
 *   <tr><td>{@code POST /booking}</td><td><b>200</b>, not 201</td>
 *       <td>{@code {bookingid, booking}} — wrapped</td></tr>
 *   <tr><td>{@code GET /booking/{id}}</td><td>200</td><td>the booking, unwrapped</td></tr>
 *   <tr><td>{@code GET /booking}</td><td>200</td><td>{@code [{bookingid}]} — ids only</td></tr>
 *   <tr><td>{@code PUT /booking/{id}}</td><td>200</td><td>updated booking, unwrapped</td></tr>
 *   <tr><td>{@code PATCH /booking/{id}}</td><td>200</td><td>updated booking, unwrapped</td></tr>
 *   <tr><td>{@code DELETE /booking/{id}}</td><td><b>201 Created</b></td><td>{@code Created}</td></tr>
 *   <tr><td>{@code DELETE} again</td><td><b>405</b></td><td>—</td></tr>
 *   <tr><td>{@code GET} absent id</td><td>404</td><td>{@code Not Found}, plain text</td></tr>
 *   <tr><td>{@code POST} invalid body</td><td><b>500</b></td><td>plain text</td></tr>
 * </table>
 *
 * <p>The methods below return {@link ApiResponse} rather than throwing on a non-2xx, so a
 * negative test can assert on the status. Writes go through
 * {@link AuthenticatedClient#sendAuthenticated} and so survive a stale token.
 */
public final class BookingClient extends AuthenticatedClient {

    private static final String BOOKINGS = "/booking";

    public BookingClient(Specs specs, AuthTokenCache tokens) {
        super(specs, tokens);
    }

    /** Creates a booking. Needs no token: this API allows anonymous creates. */
    public ApiResponse<CreatedBooking> create(BookingRequest booking) {
        Response response = send("POST " + BOOKINGS, () ->
                request().body(booking).post(BOOKINGS));

        return bind(response, CreatedBooking.class);
    }

    /**
     * Creates a booking from a raw map.
     *
     * <p>Exists for the validation suite: a payload with a key <em>absent</em> cannot be
     * expressed through {@link BookingRequest}, where an unset field serialises as
     * {@code null}. The server distinguishes the two, so the framework must too.
     */
    public ApiResponse<CreatedBooking> createRaw(Map<String, Object> payload) {
        Response response = send("POST " + BOOKINGS + " (raw)", () ->
                request().body(payload).post(BOOKINGS));

        return bind(response, CreatedBooking.class);
    }

    /** Reads one booking. */
    public ApiResponse<Booking> getById(int id) {
        Response response = send("GET " + BOOKINGS + "/" + id, () ->
                request().get(BOOKINGS + "/" + id));

        return bind(response, Booking.class);
    }

    /**
     * Reads every booking id.
     *
     * <p>Returns ids, not bookings: that is what the endpoint gives. Bound by hand because
     * the body is a JSON array, which {@link #bind} cannot express with a single class.
     */
    public ApiResponse<List<BookingId>> getAllIds() {
        Response response = send("GET " + BOOKINGS, () -> request().get(BOOKINGS));

        return ApiResponse.from(response, raw ->
                Arrays.asList(Json.read(raw, BookingId[].class)));
    }

    /**
     * Posts a raw map and does not bind the response.
     *
     * <p>For validation cases, where an accepted-but-invalid payload can come back in a shape
     * the model rejects: an unparseable checkin is stored and returned as
     * {@code "0NaN-aN-aN"}, which is not a date. {@link #createRaw} binds strictly on 2xx,
     * which is right everywhere else, so this is the one place the raw body is the result.
     */
    public ApiResponse<Void> submitRaw(Map<String, Object> payload) {
        Response response = send("POST " + BOOKINGS + " (raw, unbound)", () ->
                request().body(payload).post(BOOKINGS));

        return noBody(response);
    }

    /**
     * Posts a body exactly as given, without serialising anything.
     *
     * <p>The only way to send malformed JSON, which this API answers with 400 rather than
     * the 500 it gives for well-formed but invalid payloads.
     */
    public ApiResponse<CreatedBooking> createFromJson(String rawJson) {
        Response response = send("POST " + BOOKINGS + " (raw json)", () ->
                request().body(rawJson).post(BOOKINGS));

        return bind(response, CreatedBooking.class);
    }

    /**
     * Booking ids matching the given query filters, e.g. {@code firstname}, {@code lastname}.
     *
     * <p>An empty list, not a 404, when nothing matches (observed 2026-11-23).
     */
    public ApiResponse<List<BookingId>> search(Map<String, ?> filters) {
        Response response = send("GET " + BOOKINGS + " " + filters, () ->
                request().queryParams(filters).get(BOOKINGS));

        return ApiResponse.from(response, raw ->
                Arrays.asList(Json.read(raw, BookingId[].class)));
    }

    /**
     * Deletes with exactly the token given, and no refresh.
     *
     * <p>For negative tests. {@link #delete} would treat the expected 403 as a stale token,
     * log in again and succeed, which is right for a suite and defeats a test whose whole
     * point is the 403. Pass {@code null} to send no cookie at all.
     */
    public ApiResponse<Void> deleteWithToken(int id, String token) {
        Response response = send("DELETE " + BOOKINGS + "/" + id + " (explicit token)", () -> {
            var request = request();
            if (token != null) {
                request.cookie(TOKEN_COOKIE, token);
            }
            return request.delete(BOOKINGS + "/" + id);
        });

        return noBody(response);
    }

    /** Replaces a booking. Requires a token. */
    public ApiResponse<Booking> update(int id, BookingRequest booking) {
        Response response = sendAuthenticated("PUT " + BOOKINGS + "/" + id, token ->
                request().cookie(TOKEN_COOKIE, token).body(booking).put(BOOKINGS + "/" + id));

        return bind(response, Booking.class);
    }

    /** Changes some fields of a booking. Requires a token. */
    public ApiResponse<Booking> partialUpdate(int id, Map<String, Object> changes) {
        Response response = sendAuthenticated("PATCH " + BOOKINGS + "/" + id, token ->
                request().cookie(TOKEN_COOKIE, token).body(changes).patch(BOOKINGS + "/" + id));

        return bind(response, Booking.class);
    }

    /**
     * Deletes a booking. Requires a token.
     *
     * <p>Answers <b>201 Created</b> on success, which is simply wrong of the API but is
     * what it does, so {@link #isDeleted} exists rather than letting callers guess. A
     * second delete of the same id returns 405.
     */
    public ApiResponse<Void> delete(int id) {
        Response response = sendAuthenticated("DELETE " + BOOKINGS + "/" + id, token ->
                request().cookie(TOKEN_COOKIE, token).delete(BOOKINGS + "/" + id));

        return noBody(response);
    }

    /**
     * Whether a delete response means the booking is gone.
     *
     * <p>201 for a successful delete, 405 when it had already been deleted. Both mean the
     * resource is absent, which is what cleanup actually cares about.
     */
    public static boolean isDeleted(int statusCode) {
        return statusCode == 201 || statusCode == 405 || statusCode == 404;
    }
}
