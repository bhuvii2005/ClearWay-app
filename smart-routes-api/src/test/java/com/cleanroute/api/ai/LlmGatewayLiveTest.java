package com.cleanroute.api.ai;

import com.cleanroute.api.support.DotEnv;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One real round trip to Groq. Runs only with {@code ./gradlew evalTest} and only when
 * GROQ_API_KEY is set (environment or .env); otherwise it is skipped. Never part of build.
 */
@Tag("eval")
class LlmGatewayLiveTest {

    @Test
    void groqRoundTrip() {
        var key = DotEnv.get("GROQ_API_KEY");
        assumeTrue(key.isPresent(), "GROQ_API_KEY not set; skipping the live Groq check");

        AiProperties settings = AiTestSettings.groq(key.get(), "https://api.groq.com/openai/v1", Duration.ofSeconds(30));
        HostedTokenBudget budget = new HostedTokenBudget(10_000, Clock.systemUTC());
        LlmGateway gateway = new LlmGateway(new ChatModelFactory(settings).create(), budget,
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20),
                d -> Thread.sleep(d.toMillis()));

        String answer = gateway.call(new Prompt("Reply with the single word: ready")).getResult().getOutput().getText();

        assertThat(answer).isNotBlank();
        assertThat(budget.usedToday()).isPositive();
    }
}
