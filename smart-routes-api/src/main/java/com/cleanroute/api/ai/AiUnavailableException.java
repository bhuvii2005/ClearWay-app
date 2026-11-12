package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A 503 from the AI layer, with why it happened. The HTTP response is the same as any
 * ApiException; the reason only feeds the {@code coach.fallbacks} counter, the per-message log
 * and the eval harness (which stops cleanly on a used-up budget or quota).
 */
public class AiUnavailableException extends ApiException {

    public enum Reason {
        /** Our own daily cap (requests or tokens) is reached. */
        BUDGET("budget"),
        /** The provider said its per-day quota is used up. */
        QUOTA_EXHAUSTED("quota_exhausted"),
        /** The per-message time budget ran out. */
        TIME_BUDGET("time_budget"),
        /** No provider set up, provider failing or slow, circuit open, too busy. */
        PROVIDER_ERROR("provider_error");

        private final String tag;

        Reason(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    private final Reason reason;

    public AiUnavailableException(Reason reason, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** True when no more calls can succeed today (our cap or the provider's quota). */
    public boolean dayIsOver() {
        return reason == Reason.BUDGET || reason == Reason.QUOTA_EXHAUSTED;
    }
}
