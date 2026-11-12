package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Embeddings through their own guard and budget, with fake models: no network. */
class EmbeddingGatewayTest {

    private final List<Duration> waits = new ArrayList<>();

    @Test
    void noGeminiKey_KnowledgeBaseIsOff() {
        EmbeddingService off = new EmbeddingService(AiTestSettings.withKnowledgeBase(0.6, 20, 30),
                Optional.empty(), Optional.empty(), CircuitBreaker.ofDefaults("e"), Bulkhead.ofDefaults("e"), waits::add);
        assertThat(off.isConfigured()).isFalse();
        assertThat(off.forDocuments()).isEmpty();
    }

    @Test
    void aListOfDocumentsIsOneRequest_CountedInTheEmbeddingBudgetAndTally() {
        FakeEmbeddingModel fake = new FakeEmbeddingModel();
        EmbeddingService service = service(fake);
        List<Document> docs = List.of(new Document("one"), new Document("two"), new Document("three"));

        try (AiUsageTally tally = AiUsageTally.open("test")) {
            List<float[]> vectors = service.forDocuments().orElseThrow().embed(docs, EmbeddingOptions.builder().build(), null);
            service.forQueries().orElseThrow().embed("a question");

            assertThat(vectors).hasSize(3);
            assertThat(tally.embeddingRequests()).isEqualTo(2);
            assertThat(tally.chatRequests()).isZero();
        }
        assertThat(fake.batchSizes).containsExactly(3, 1);
        assertThat(service.budget().requestsToday()).isEqualTo(2);
    }

    @Test
    void rateLimited_RetriesOnceThen503_AndBudgetStopsFurtherRequests() {
        FakeEmbeddingModel flaky = new FakeEmbeddingModel(n -> n == 1 ? tooManyRequests() : null);
        EmbeddingService service = service(flaky);
        assertThat(service.forQueries().orElseThrow().embed("q")).hasSize(768);
        assertThat(flaky.requests).hasValue(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(3));

        FakeEmbeddingModel always429 = new FakeEmbeddingModel(n -> tooManyRequests());
        EmbeddingService limited = service(always429);
        assertThatThrownBy(() -> limited.forQueries().orElseThrow().embed("q"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(always429.requests).hasValue(2);
        assertThat(limited.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void wrongVectorSize_IsRejected() {
        ProviderGuard guard = new ProviderGuard("embedding", new HostedAiBudget(10, Long.MAX_VALUE, java.time.Clock.systemUTC()),
                CircuitBreaker.ofDefaults("x"), Bulkhead.ofDefaults("x"), Duration.ofSeconds(20), waits::add);
        EmbeddingGateway expects1536 = new EmbeddingGateway(new FakeEmbeddingModel(), guard, 1536);

        assertThatThrownBy(() -> expects1536.embed(List.of(new Document("x")), EmbeddingOptions.builder().build(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("1536");
    }

    @Test
    void health_ShowsTheLastEmbeddingCall_WithoutCallingIt_AndStaysUp() {
        // Call 1 succeeds; calls 2 and 3 are 429s (one retry), so call 2 ends in a 503.
        FakeEmbeddingModel model = new FakeEmbeddingModel(n -> n >= 2 ? tooManyRequests() : null);
        EmbeddingService service = service(model);
        LlmGateway noChat = new LlmGateway(Optional.empty(), new HostedAiBudget(10, 10_000, java.time.Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), waits::add);
        AiHealthIndicator health = new AiHealthIndicator(noChat, service);

        assertThat(health.health().getDetails()).containsEntry("lastEmbeddingCall", "none yet");
        assertThat(model.requests).hasValue(0); // health made no call

        service.forQueries().orElseThrow().embed("q");
        assertThat(health.health().getDetails().get("lastEmbeddingCall"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("result", "ok").containsKey("at");

        assertThatThrownBy(() -> service.forQueries().orElseThrow().embed("q")).isInstanceOf(ApiException.class);
        var details = health.health();
        assertThat(details.getStatus().getCode()).isEqualTo("UP");
        assertThat(details.getDetails().get("lastEmbeddingCall"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("result", "failed").containsEntry("reason", "provider_error").containsKey("at");
        assertThat(model.requests).hasValue(3);
    }

    @Test
    void chunkTitleIsEmbeddedWithTheText() {
        EmbeddingGateway gateway = service(new FakeEmbeddingModel()).forDocuments().orElseThrow();
        Document chunk = new Document("PM2.5 guideline is 15.", Map.of(KnowledgeBaseMetadata.TITLE, "WHO guidelines"));
        assertThat(gateway.getEmbeddingContent(chunk)).isEqualTo("WHO guidelines\n\nPM2.5 guideline is 15.");
    }

    private EmbeddingService service(FakeEmbeddingModel model) {
        return new EmbeddingService(AiTestSettings.withKnowledgeBase(0.6, 20, 30), Optional.of(model), Optional.of(model),
                CircuitBreaker.ofDefaults("embedding"), Bulkhead.ofDefaults("embedding"), waits::add);
    }

    private static RuntimeException tooManyRequests() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "3");
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", headers, new byte[0], StandardCharsets.UTF_8);
    }
}
