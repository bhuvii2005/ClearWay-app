package com.cleanroute.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds up the provider requests and tokens behind one piece of work, such as one Coach message
 * (which is several LLM calls: tool rounds plus the final answer, and an embedding request per
 * knowledge-base search). {@link LlmGateway} and {@link EmbeddingGateway} record into the tally
 * open on the current thread; closing it logs the totals (no user text).
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
    private int chatRequests;
    private long chatTokens;
    private int embeddingRequests;

    private AiUsageTally(String label, AiUsageTally outer) {
        this.label = label;
        this.outer = outer;
    }

    public static AiUsageTally open(String label) {
        AiUsageTally tally = new AiUsageTally(label, CURRENT.get());
        CURRENT.set(tally);
        return tally;
    }

    static void recordChatRequest() {
        for (AiUsageTally t = CURRENT.get(); t != null; t = t.outer) {
            t.chatRequests++;
        }
    }

    static void recordChatTokens(long count) {
        if (count <= 0) {
            return;
        }
        for (AiUsageTally t = CURRENT.get(); t != null; t = t.outer) {
            t.chatTokens += count;
        }
    }

    static void recordEmbeddingRequest() {
        for (AiUsageTally t = CURRENT.get(); t != null; t = t.outer) {
            t.embeddingRequests++;
        }
    }

    public int chatRequests() {
        return chatRequests;
    }

    public long chatTokens() {
        return chatTokens;
    }

    public int embeddingRequests() {
        return embeddingRequests;
    }

    @Override
    public void close() {
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
        log.info("AI usage for {}: {} chat requests, {} chat tokens, {} embedding requests",
                label, chatRequests, chatTokens, embeddingRequests);
    }
}
