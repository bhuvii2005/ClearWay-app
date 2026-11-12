package com.cleanroute.api.exception;

import org.springframework.http.HttpStatus;

/** A 429 error that also tells the client how many seconds to wait (the Retry-After header). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
