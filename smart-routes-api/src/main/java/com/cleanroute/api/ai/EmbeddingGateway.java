package com.cleanroute.api.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * An {@link EmbeddingModel} that sends every request through the embedding {@link ProviderGuard}
 * (own daily budget, bulkhead, circuit breaker, one retry on 429) and counts it in the caller's
 * {@link AiUsageTally}. A list of documents is always embedded in exactly one request, so the
 * caller decides the batch size and can space batches for the per-minute limit.
 */
public final class EmbeddingGateway implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final ProviderGuard guard;
    private final int dimensions;

    EmbeddingGateway(EmbeddingModel delegate, ProviderGuard guard, int dimensions) {
        this.delegate = delegate;
        this.guard = guard;
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return guard.call(() -> delegate.call(request), AiUsageTally::recordEmbeddingRequest);
    }

    @Override
    public float[] embed(Document document) {
        return embed(List.of(document), EmbeddingOptions.builder().build(), null).get(0);
    }

    /** One request for the whole list (the batching strategy is ignored on purpose). */
    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy ignored) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<String> texts = documents.stream().map(this::getEmbeddingContent).toList();
        EmbeddingResponse response = call(new EmbeddingRequest(texts, options));
        List<float[]> vectors = response.getResults().stream().map(Embedding::getOutput).toList();
        if (vectors.size() != documents.size()) {
            throw new IllegalStateException("Expected " + documents.size() + " embeddings, got " + vectors.size());
        }
        for (float[] vector : vectors) {
            if (vector.length != dimensions) {
                throw new IllegalStateException("Expected " + dimensions + "-dimension embeddings, got " + vector.length);
            }
        }
        return vectors;
    }

    /** The chunk's title plus its text: the title helps a short chunk match the right questions. */
    @Override
    public String getEmbeddingContent(Document document) {
        Object title = document.getMetadata().get(KnowledgeBaseMetadata.TITLE);
        return title == null ? document.getText() : title + "\n\n" + document.getText();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
