package io.github.bhushanupwanshi.api.core.models.response;

/**
 * The body of a {@code POST /auth} response.
 *
 * <p>Both fields are nullable, and exactly one is present, because the endpoint answers
 * <strong>HTTP 200 either way</strong>: a success carries {@code token}, a rejection
 * carries {@code reason} and the same 200. Verified against restful-booker on 2026-10-06.
 *
 * <p>That is why this is one record with two optional fields rather than two types
 * selected by status code. Modelling it the textbook way, with the status deciding, would
 * make every failed login look like a success with a null token, and the first symptom
 * would be a 403 several calls later.
 *
 * @param token  issued token, or {@code null} when the credentials were rejected
 * @param reason why they were rejected, or {@code null} on success
 */
public record AuthResponse(String token, String reason) {

    /** Whether a token was issued. The only reliable success check for this endpoint. */
    public boolean isSuccessful() {
        return token != null && !token.isBlank();
    }
}
