package com.cleanroute.api.ai;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side state for one Coach message, passed to the tools through Spring AI's ToolContext
 * (which never reaches the model). It carries the signed-in user's id, taken from the JWT, and
 * collects the tools used and the knowledge-base chunks returned, so the reply's citations are
 * built from what was actually retrieved rather than from anything the model wrote.
 */
public final class CoachTurn {

    /** The ToolContext key this object is stored under. */
    public static final String KEY = "clearway.coachTurn";

    private final UUID userId;
    private final ZoneId timeZone;
    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private final Map<String, Citation> citations = new LinkedHashMap<>();

    public CoachTurn(UUID userId, ZoneId timeZone) {
        this.userId = userId;
        this.timeZone = timeZone;
    }

    public UUID userId() {
        return userId;
    }

    public ZoneId timeZone() {
        return timeZone;
    }

    synchronized void toolUsed(String name) {
        toolsUsed.add(name);
    }

    synchronized void retrieved(List<KnowledgeChunk> chunks) {
        for (KnowledgeChunk chunk : chunks) {
            citations.putIfAbsent(chunk.sourceUrl(), new Citation(chunk.title(), chunk.sourceUrl()));
        }
    }

    public synchronized List<String> toolsUsed() {
        return new ArrayList<>(toolsUsed);
    }

    public synchronized List<Citation> citations() {
        return new ArrayList<>(citations.values());
    }

    /** One source, deduplicated by URL. */
    public record Citation(String title, String url) {
    }
}
