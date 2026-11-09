package com.cleanroute.api.ai;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Start-up validation of clearway.ai.*, provider selection, and the health detail. No network. */
class AiSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfig.class, ChatModelFactory.class)
            .withPropertyValues(
                    "clearway.ai.enabled=true",
                    "clearway.ai.chat.provider=gemini",
                    "clearway.ai.chat.temperature=0.2",
                    "clearway.ai.chat.max-output-tokens=800",
                    "clearway.ai.chat.timeout=30s",
                    "clearway.ai.providers.groq.base-url=https://api.groq.com/openai/v1",
                    "clearway.ai.providers.groq.chat-model=openai/gpt-oss-20b",
                    "clearway.ai.providers.gemini.base-url=https://generativelanguage.googleapis.com/v1beta/openai/",
                    "clearway.ai.providers.gemini.chat-model=gemini-3.8-flash",
                    "clearway.ai.embedding.model=gemini-embedding-001",
                    "clearway.ai.embedding.dimensions=768",
                    "clearway.ai.embedding.timeout=20s",
                    "clearway.ai.embedding.daily-request-budget=500",
                    "clearway.ai.kb.ingest-on-startup=true",
                    "clearway.ai.kb.top-k=4",
                    "clearway.ai.kb.similarity-threshold=0.6",
                    "clearway.ai.kb.chunk-size-tokens=300",
                    "clearway.ai.kb.batch-size=20",
                    "clearway.ai.kb.embed-requests-per-minute=30",
                    "clearway.ai.limits.messages-per-hour=20",
                    "clearway.ai.limits.max-input-chars=2000",
                    "clearway.ai.limits.daily-request-budget=200",
                    "clearway.ai.limits.daily-token-budget=300000",
                    "clearway.ai.limits.budget-reset-zone=America/Los_Angeles",
                    "clearway.ai.limits.max-retry-wait=20s");

    @Test
    void noKey_StartsFineWithCoachOff() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty();
        });
    }

    @Test
    void placeholderKeyFromEnvExample_CountsAsMissing() {
        runner.withPropertyValues("clearway.ai.providers.gemini.api-key=change_me_gemini_key")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty());
    }

    @Test
    void keySet_BuildsTheChosenProviderWithoutCallingIt() {
        runner.withPropertyValues("clearway.ai.providers.gemini.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).hasValueSatisfying(m -> {
                    assertThat(m.provider()).isEqualTo(AiProperties.Provider.GEMINI);
                    assertThat(m.model()).isEqualTo("gemini-3.8-flash");
                }));

        // Groq is optional: chosen by config, and a Gemini key alone doesn't switch it on.
        runner.withPropertyValues("clearway.ai.chat.provider=groq", "clearway.ai.providers.groq.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create())
                        .hasValueSatisfying(m -> assertThat(m.provider()).isEqualTo(AiProperties.Provider.GROQ)));
        runner.withPropertyValues("clearway.ai.chat.provider=groq", "clearway.ai.providers.gemini.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty());
    }

    @Test
    void disabled_TurnsCoachOffEvenWithAKey() {
        runner.withPropertyValues("clearway.ai.enabled=false", "clearway.ai.providers.gemini.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty());
    }

    @Test
    void nonsenseValues_FailAtStartUp() {
        runner.withPropertyValues("clearway.ai.chat.temperature=5").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.chat.timeout=0s").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.chat.provider=openai").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.embedding.dimensions=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.limits.messages-per-hour=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.limits.daily-request-budget=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.limits.budget-reset-zone=Mars/Olympus").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.kb.similarity-threshold=1.5").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.kb.batch-size=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.embedding.daily-request-budget=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void keyNeverAppearsInToString() {
        var settings = new AiProperties.ProviderSettings("gsk_secret_value", "https://x", "m");
        assertThat(settings.toString()).doesNotContain("gsk_secret_value").contains("key=set");
    }

    @Test
    void health_IsUpEvenWhenCoachIsOff() {
        LlmGateway off = new LlmGateway(Optional.empty(), new HostedAiBudget(10, 10, Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), d -> { });
        EmbeddingService noEmbeddings = new EmbeddingService(AiTestSettings.withKnowledgeBase(0.6, 20, 30),
                Optional.empty(), Optional.empty(), CircuitBreaker.ofDefaults("e"), Bulkhead.ofDefaults("e"), d -> { });

        var health = new AiHealthIndicator(off, noEmbeddings).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("coachAvailable", false).containsKey("reason")
                .containsEntry("knowledgeBaseAvailable", false);
    }

    @Test
    void geminiKey_BuildsBothEmbeddingModelsWithoutCallingThem_EvenWhenChatUsesGroq() {
        var settings = AiTestSettings.forProvider(AiProperties.Provider.GROQ, "test-key-not-real", AiTestSettings.GROQ_URL, Duration.ofSeconds(5));
        var withGeminiKey = new AiProperties(true, settings.chat(),
                new AiProperties.Providers(settings.providers().groq(),
                        new AiProperties.ProviderSettings("test-key-not-real", AiTestSettings.GEMINI_URL, "gemini-3.8-flash")),
                settings.embedding(), settings.kb(), settings.limits());

        EmbeddingService service = new EmbeddingService(withGeminiKey,
                io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(),
                io.github.resilience4j.bulkhead.BulkheadRegistry.ofDefaults());

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.forDocuments().orElseThrow().dimensions()).isEqualTo(768);
        assertThat(service.model()).isEqualTo("gemini-embedding-001");
    }
}
