package com.cleanroute.api.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Gemini embeddings for the knowledge base. Two models share one guard and one daily budget
 * (Gemini's embedding quota is separate from chat): RETRIEVAL_DOCUMENT for ingestion and
 * RETRIEVAL_QUERY for search, as Gemini recommends. Without a Gemini key both are absent and the
 * knowledge base is off; the rest of the app is unaffected.
 */
@Component
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final String model;
    private final int dimensions;
    private final ProviderGuard guard;
    private final Optional<EmbeddingGateway> forDocuments;
    private final Optional<EmbeddingGateway> forQueries;

    @Autowired
    public EmbeddingService(AiProperties properties, CircuitBreakerRegistry circuitBreakers, BulkheadRegistry bulkheads) {
        this(properties, geminiModel(properties, TaskType.RETRIEVAL_DOCUMENT), geminiModel(properties, TaskType.RETRIEVAL_QUERY),
                circuitBreakers.circuitBreaker("embedding"), bulkheads.bulkhead("embedding"), Sleeper.REAL);
    }

    EmbeddingService(AiProperties properties, Optional<EmbeddingModel> documentModel, Optional<EmbeddingModel> queryModel,
                     CircuitBreaker circuitBreaker, Bulkhead bulkhead, Sleeper sleeper) {
        this.model = properties.embedding().model();
        this.dimensions = properties.embedding().dimensions();
        HostedAiBudget budget = new HostedAiBudget("embedding", properties.embedding().dailyRequestBudget(), Long.MAX_VALUE,
                Clock.system(properties.limits().budgetResetZone()));
        this.guard = new ProviderGuard("embedding", budget, circuitBreaker, bulkhead,
                properties.limits().maxRetryWait(), sleeper);
        this.forDocuments = documentModel.map(m -> new EmbeddingGateway(m, guard, dimensions));
        this.forQueries = queryModel.map(m -> new EmbeddingGateway(m, guard, dimensions));
    }

    public boolean isConfigured() {
        return forDocuments.isPresent() && forQueries.isPresent();
    }

    /** For ingestion (task type RETRIEVAL_DOCUMENT). */
    public Optional<EmbeddingGateway> forDocuments() {
        return forDocuments;
    }

    /** For search queries (task type RETRIEVAL_QUERY). */
    public Optional<EmbeddingGateway> forQueries() {
        return forQueries;
    }

    /** Stored with every chunk, so vectors from different models are never compared. */
    public String model() {
        return model;
    }

    public int dimensions() {
        return dimensions;
    }

    public HostedAiBudget budget() {
        return guard.budget();
    }

    public CircuitBreaker.State circuitState() {
        return guard.circuitState();
    }

    private static Optional<EmbeddingModel> geminiModel(AiProperties properties, TaskType taskType) {
        AiProperties.ProviderSettings gemini = properties.providers().gemini();
        if (!properties.enabled() || !gemini.hasKey()) {
            if (taskType == TaskType.RETRIEVAL_DOCUMENT) {
                log.warn("No GEMINI_API_KEY (or AI turned off): the Coach's knowledge base is off; the rest of the app works normally.");
            }
            return Optional.empty();
        }
        Duration timeout = properties.embedding().timeout();
        Client client = Client.builder()
                .apiKey(gemini.apiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout((int) timeout.toMillis())
                        // ProviderGuard owns retries, so neither the SDK nor Spring AI adds its own.
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(properties.embedding().model())
                .dimensions(properties.embedding().dimensions())
                .taskType(taskType)
                .build();
        return Optional.of(new GoogleGenAiTextEmbeddingModel(
                GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(client).build(),
                options, new RetryTemplate(RetryPolicy.withMaxRetries(0))));
    }
}
