package com.cleanroute.api.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real round trips to the eval provider (Gemini unless CLEARWAY_EVAL_PROVIDER says otherwise).
 * Runs only with {@code ./gradlew evalTest} and only when that provider's key is set
 * (environment or .env); otherwise it is skipped. Never part of build.
 */
@Tag("eval")
class LlmGatewayLiveTest {

    @Test
    void liveRoundTrip_CountsRequestsAndTokensPerMessage() {
        EvalSettings eval = EvalSettings.load();
        assumeTrue(eval.apiKey.isPresent(), eval.keyName() + " not set; skipping the live " + eval.provider + " check");

        HostedAiBudget budget = eval.budget();
        LlmGateway gateway = eval.gateway(budget);

        // Two calls under one tally, like a Coach message with one tool round; the pacer spaces them.
        try (AiUsageTally tally = AiUsageTally.open("live eval message")) {
            String first = gateway.call(new Prompt("Reply with the single word: ready")).getResult().getOutput().getText();
            String second = gateway.call(new Prompt("Reply with the single word: done")).getResult().getOutput().getText();

            assertThat(first).isNotBlank();
            assertThat(second).isNotBlank();
            assertThat(tally.chatRequests()).isGreaterThanOrEqualTo(2);
            assertThat(tally.chatTokens()).isPositive();
        }
        assertThat(budget.requestsToday()).isGreaterThanOrEqualTo(2);
    }
}
