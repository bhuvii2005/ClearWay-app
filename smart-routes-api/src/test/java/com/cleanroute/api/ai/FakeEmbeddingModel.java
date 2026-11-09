package com.cleanroute.api.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * A deterministic, offline embedding model: each word is hashed into one of 768 slots, so texts
 * sharing words point in similar directions. Good enough to rank the knowledge base in tests.
 * {@code failOn} can make chosen requests (1-based) throw, to simulate a 429 midway.
 */
final class FakeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 768;

    final AtomicInteger requests = new AtomicInteger();
    final List<Integer> batchSizes = new ArrayList<>();
    private final IntFunction<RuntimeException> failOn;

    FakeEmbeddingModel() {
        this(n -> null);
    }

    FakeEmbeddingModel(IntFunction<RuntimeException> failOn) {
        this.failOn = failOn;
    }

    @Override
    public synchronized EmbeddingResponse call(EmbeddingRequest request) {
        int n = requests.incrementAndGet();
        RuntimeException failure = failOn.apply(n);
        if (failure != null) {
            throw failure;
        }
        batchSizes.add(request.getInstructions().size());
        List<Embedding> out = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            out.add(new Embedding(vector(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9.₂₃]+")) {
            String w = word.replaceAll("^\\.+|\\.+$", "");
            if (w.length() > 2) {
                v[Math.floorMod(w.hashCode(), DIMENSIONS)] += 1;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1;
            return v;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= scale;
        }
        return v;
    }
}
