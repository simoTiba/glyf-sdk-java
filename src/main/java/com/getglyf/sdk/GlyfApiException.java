package com.getglyf.sdk;

/**
 * The GLYF API rejected the request (non-2xx response).
 *
 * <p>Carries the HTTP status, the machine-readable error code (e.g.
 * {@code "RATE_LIMIT_EXCEEDED"}, {@code "INVALID_API_KEY"}) and, for 429
 * responses, the server-suggested wait before retrying.</p>
 */
public class GlyfApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Integer retryAfterSeconds;

    public GlyfApiException(int status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** HTTP status code, e.g. 401, 429, 500. */
    public int status() {
        return status;
    }

    /** Machine-readable error code from the API error payload, or {@code null}. */
    public String code() {
        return code;
    }

    /** Seconds to wait before retrying (429 responses), or {@code null}. */
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String toString() {
        return "GlyfApiException{status=" + status + ", code=" + code + ", message=" + getMessage() + "}";
    }
}
