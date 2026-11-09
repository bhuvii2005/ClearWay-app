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

    public List<KnowledgeChunk> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (store.isEmpty()) {
            log.debug("Knowledge base search skipped: no embedding model configured");
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query.strip())
                .topK(topK)
                .similarityThreshold(threshold)
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
