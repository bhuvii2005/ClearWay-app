package com.cleanroute.api.ai;

/**
 * One search hit from the knowledge base, with what the Coach needs to cite it.
 *
 * @param score cosine similarity, 0 to 1 (higher is closer)
 */
public record KnowledgeChunk(String title, String sourceUrl, String sourceFile, String text, double score) {
}
