package com.cleanroute.api.ai;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What went wrong in a call to an AI provider, in provider-neutral terms.
 *
 * @param status     HTTP status from the provider, or 0 for network errors and timeouts
 * @param retryAfter how long the provider asked us to wait, when it said so
 */
public record ProviderFailure(int status, Optional<Duration> retryAfter) {

    /** Gemini's SDK has no Retry-After header; its 429 says "Please retry in 17.5s." or "retryDelay": "17s". */
    private static final Pattern GEMINI_RETRY_DELAY =
            Pattern.compile("(?i)(?:retry in|\"retryDelay\"\\s*:\\s*\")\\s*([0-9]+(?:\\.[0-9]+)?)\\s*s");

    public boolean rateLimited() {
        return status == 429;
    }

    /** Only "slow down" (429) and provider-side errors (5xx) are worth one more try. */
    public boolean retryable() {
        return status == 429 || status >= 500;
    }

    /** Reads the status and Retry-After from the exception or anything in its cause chain. */
    public static ProviderFailure of(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof OpenAIServiceException e) {
                return new ProviderFailure(e.statusCode(), retryAfter(e.headers().values("retry-after")));
            }
            if (t instanceof RestClientResponseException e) {
                var values = e.getResponseHeaders() == null ? List.<String>of() : e.getResponseHeaders().getOrEmpty("Retry-After");
                return new ProviderFailure(e.getStatusCode().value(), retryAfter(values));
            }
            if (t instanceof ApiException e) {
                return new ProviderFailure(e.code(), geminiRetryDelay(e.getMessage()));
            }
            if (t instanceof OpenAIIoException || t instanceof GenAiIOException) {
                return new ProviderFailure(0, Optional.empty());
            }
        }
        return new ProviderFailure(0, Optional.empty());
    }

    /** Retry-After in seconds (Groq may send decimals such as "2.5"); HTTP dates are ignored. */
    static Optional<Duration> retryAfter(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            double seconds = Double.parseDouble(values.get(0).trim());
            return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofMillis((long) Math.ceil(seconds * 1000)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<Duration> geminiRetryDelay(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher m = GEMINI_RETRY_DELAY.matcher(message);
        return m.find() ? retryAfter(List.of(m.group(1))) : Optional.empty();
    }
}
