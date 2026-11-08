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
                    "clearway.ai.chat.provider=groq",
                    "clearway.ai.chat.temperature=0.2",
                    "clearway.ai.chat.max-output-tokens=800",
                    "clearway.ai.chat.timeout=30s",
                    "clearway.ai.providers.groq.base-url=https://api.groq.com/openai/v1",
                    "clearway.ai.providers.groq.chat-model=openai/gpt-oss-20b",
                    "clearway.ai.providers.gemini.base-url=https://generativelanguage.googleapis.com/v1beta/openai/",
                    "clearway.ai.providers.gemini.chat-model=gemini-3.8-flash",
                    "clearway.ai.embedding.model=gemini-embedding-001",
                    "clearway.ai.embedding.dimensions=768",
                    "clearway.ai.limits.messages-per-hour=20",
                    "clearway.ai.limits.max-input-chars=2000",
                    "clearway.ai.limits.daily-token-budget=150000",
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
        runner.withPropertyValues("clearway.ai.providers.groq.api-key=change_me_groq_key")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty());
    }

    @Test
    void keySet_BuildsTheChosenProviderWithoutCallingIt() {
        runner.withPropertyValues("clearway.ai.providers.groq.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).hasValueSatisfying(m -> {
                    assertThat(m.provider()).isEqualTo(AiProperties.Provider.GROQ);
                    assertThat(m.model()).isEqualTo("openai/gpt-oss-20b");
                }));

        runner.withPropertyValues("clearway.ai.chat.provider=gemini", "clearway.ai.providers.gemini.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create())
                        .hasValueSatisfying(m -> assertThat(m.provider()).isEqualTo(AiProperties.Provider.GEMINI)));
    }

    @Test
    void disabled_TurnsCoachOffEvenWithAKey() {
        runner.withPropertyValues("clearway.ai.enabled=false", "clearway.ai.providers.groq.api-key=test-key-not-real")
                .run(ctx -> assertThat(ctx.getBean(ChatModelFactory.class).create()).isEmpty());
    }

    @Test
    void nonsenseValues_FailAtStartUp() {
        runner.withPropertyValues("clearway.ai.chat.temperature=5").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.chat.timeout=0s").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.chat.provider=openai").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.embedding.dimensions=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("clearway.ai.limits.messages-per-hour=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void keyNeverAppearsInToString() {
        var settings = new AiProperties.ProviderSettings("gsk_secret_value", "https://x", "m");
        assertThat(settings.toString()).doesNotContain("gsk_secret_value").contains("key=set");
    }

    @Test
    void health_IsUpEvenWhenCoachIsOff() {
        LlmGateway off = new LlmGateway(Optional.empty(), new HostedTokenBudget(10, Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), d -> { });

        var health = new AiHealthIndicator(off).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("coachAvailable", false).containsKey("reason");
    }
}
