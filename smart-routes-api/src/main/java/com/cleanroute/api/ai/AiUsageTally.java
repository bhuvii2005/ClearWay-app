package com.cleanroute.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds up the provider requests and tokens behind one piece of work, such as one Coach message
 * (which is several LLM calls: tool rounds plus the final answer). {@link LlmGateway} records
 * into the tally open on the current thread; closing it logs the totals (no user text).
 *
 * <pre>
 * try (AiUsageTally tally = AiUsageTally.open("coach message")) {
 *     ... chatClient.prompt(...).call() ...
 * }
 * </pre>
 */
public final class AiUsageTally implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AiUsageTally.class);
    private static final ThreadLocal<AiUsageTally> CURRENT = new ThreadLocal<>();

    private final String label;
    private final AiUsageTally outer;
    private int requests;
    private long tokens;

    private AiUsageTally(String label, AiUsageTally outer) {
        this.label = label;
        this.outer = outer;
    }

    public static AiUsageTally open(String label) {
        AiUsageTally tally = new AiUsageTally(label, CURRENT.get());
        CURRENT.set(tally);
        return tally;
    }

    static void recordRequest() {
        for (AiUsageTally t = CURRENT.get(); t != null; t = t.outer) {
            t.requests++;
        }
    }

    static void recordTokens(long count) {
        if (count <= 0) {
            return;
        }
        for (AiUsageTally t = CURRENT.get(); t != null; t = t.outer) {
            t.tokens += count;
        }
    }

    public int requests() {
        return requests;
    }

    public long tokens() {
        return tokens;
    }

    @Override
    public void close() {
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
        log.info("AI usage for {}: {} requests, {} tokens", label, requests, tokens);
    }
}
