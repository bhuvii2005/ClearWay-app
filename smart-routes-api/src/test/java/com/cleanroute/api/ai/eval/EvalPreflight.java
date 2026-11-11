package com.cleanroute.api.ai.eval;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app and the evals share one daily quota, so before a run the harness asks the running app
 * how many chat requests it has sent today (gauge {@code clearway.ai.budget.requests{budget=chat}},
 * behind the actuator's HTTP Basic login) and refuses when it is over the limit.
 * If the app isn't running or the login isn't set, the run goes ahead and the report says the
 * app's usage couldn't be checked.
 */
public final class EvalPreflight {

    private EvalPreflight() {
    }

    /** @param refusal why the run must not start, or null to go ahead; {@code note} is for the report */
    public record Decision(String refusal, String note) {
        public boolean refused() {
            return refusal != null;
        }
    }

    public static Decision decide(OptionalLong appRequestsToday, long limit) {
        if (appRequestsToday.isEmpty()) {
            return new Decision(null, "The app's usage today couldn't be checked (app not running, or no metrics login).");
        }
        long used = appRequestsToday.getAsLong();
        if (used > limit) {
            return new Decision("The app has already sent " + used + " chat requests today, more than " + limit
                    + ". The daily quota is shared, so the eval was not run.", null);
        }
        return new Decision(null, "The app had sent " + used + " chat requests today before this run (limit " + limit + ").");
    }

    private static final Pattern VALUE = Pattern.compile("\"statistic\"\\s*:\\s*\"VALUE\"\\s*,\\s*\"value\"\\s*:\\s*([0-9.]+)");

    /** Reads the app's chat requests today, or empty if it can't. Never prints the login. */
    public static OptionalLong appChatRequestsToday(String appUrl, String user, String password) {
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(appUrl.replaceAll("/+$", "")
                            + "/actuator/metrics/clearway.ai.budget.requests?tag=budget:chat"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)))
                    .GET().build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return OptionalLong.empty();
            }
            Matcher m = VALUE.matcher(response.body());
            return m.find() ? OptionalLong.of((long) Double.parseDouble(m.group(1))) : OptionalLong.empty();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return OptionalLong.empty();
        }
    }
}
