package com.cleanroute.api.ai.eval;

import com.cleanroute.api.ai.AiUnavailableException;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs golden cases one by one. When the day's budget or the provider's daily quota runs out, it
 * stops cleanly: that case and every later one are "not run" (not failed), and the report is still
 * written. Any other failure (a timeout, the provider erroring) fails just that case.
 */
public final class EvalRunner {

    private EvalRunner() {
    }

    public enum Status { PASSED, FAILED, NOT_RUN }

    /** What one case produced. Latency excludes the eval's own pacing waits. */
    public record CaseResult(GoldenSet.Case testCase, Status status, List<String> problems, String answer,
                             List<String> toolsUsed, int chatRequests, long tokens, long latencyMs) {

        public static CaseResult notRun(GoldenSet.Case c, String why) {
            return new CaseResult(c, Status.NOT_RUN, List.of(why), null, List.of(), 0, 0, 0);
        }
    }

    public interface CaseExecutor {
        CaseResult run(GoldenSet.Case c);
    }

    /** @param stopReason why the run stopped early, or null when every case ran */
    public record Outcome(List<CaseResult> results, String stopReason) {
    }

    public static Outcome run(List<GoldenSet.Case> cases, CaseExecutor executor) {
        List<CaseResult> results = new ArrayList<>();
        String stopReason = null;
        for (GoldenSet.Case c : cases) {
            if (stopReason != null) {
                results.add(CaseResult.notRun(c, stopReason));
                continue;
            }
            try {
                results.add(executor.run(c));
            } catch (AiUnavailableException e) {
                if (e.dayIsOver()) {
                    stopReason = "stopped: " + e.reason().tag() + " (" + e.getMessage() + ")";
                    results.add(CaseResult.notRun(c, stopReason));
                } else {
                    results.add(new CaseResult(c, Status.FAILED, List.of("503 " + e.reason().tag() + ": " + e.getMessage()),
                            null, List.of(), 0, 0, 0));
                }
            }
        }
        return new Outcome(results, stopReason);
    }
}
