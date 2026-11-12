package com.cleanroute.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Searches the knowledge base: the top chunks above a similarity threshold, with their title and
 * source URL for citations. An empty list means "no source", and the Coach must say so.
 * Only chunks embedded with the current model are searched, so vectors from different models are
 * never compared. One search is one embedding request (counted in the embedding budget and the
 * caller's {@link AiUsageTally}); a provider failure surfaces as ApiException(503).
 */
@Component
public class KnowledgeBaseSearch {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSearch.class);

    private final Optional<VectorStore> store;
    private final String model;
    private final int topK;
    private final double threshold;

    public KnowledgeBaseSearch(AiProperties properties, EmbeddingService embeddings, JdbcTemplate jdbc) {
        this.store = embeddings.forQueries()
                .map(gateway -> KnowledgeBaseMetadata.openStore(jdbc, gateway, embeddings.dimensions()));
        this.model = embeddings.model();
        this.topK = properties.kb().topK();
        this.threshold = properties.kb().similarityThreshold();
    }

    public boolean isAvailable() {
        return store.isPresent();
    }

    public double threshold() {
        return threshold;
    }

    public int topK() {
        return topK;
    }

    public List<KnowledgeChunk> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (store.isEmpty()) {
            log.debug("Knowledge base search skipped: no embedding model configured");
            return List.of();
        }
        List<KnowledgeChunk> all = ranked(query);
        List<KnowledgeChunk> hits = all.stream().filter(c -> c.score() >= threshold).toList();
        // Scores only, never the query text: it is the user's own words.
        log.info("Knowledge base search: top score {}, {} of {} results at or above the {} threshold",
                all.isEmpty() ? "none" : String.format(Locale.ROOT, "%.3f", all.get(0).score()),
                hits.size(), all.size(), threshold);
        return hits;
    }

    /**
     * The top-k chunks for a query, best first, with no similarity cutoff. {@link #search} applies
     * the threshold to this; the retrieval eval uses it to score several thresholds from one search.
     */
    public List<KnowledgeChunk> ranked(String query) {
        if (store.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query.strip())
                .topK(topK)
                // No cutoff in the query, so the top score is known (and logged) even when nothing passes.
                .similarityThresholdAll()
                .filterExpression(new FilterExpressionBuilder().eq(KnowledgeBaseMetadata.EMBEDDING_MODEL, model).build())
                .build();
        return store.get().similaritySearch(request).stream().map(KnowledgeBaseSearch::toChunk).toList();
    }

    private static KnowledgeChunk toChunk(Document doc) {
        var meta = doc.getMetadata();
        return new KnowledgeChunk(
                String.valueOf(meta.get(KnowledgeBaseMetadata.TITLE)),
                String.valueOf(meta.get(KnowledgeBaseMetadata.SOURCE_URL)),
                String.valueOf(meta.get(KnowledgeBaseMetadata.SOURCE_FILE)),
                doc.getText(),
                doc.getScore() == null ? 0 : doc.getScore());
    }
}
