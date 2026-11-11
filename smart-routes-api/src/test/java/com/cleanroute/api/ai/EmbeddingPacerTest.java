package com.cleanroute.api.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ingestion pacing by requests and tokens per minute, on a fake clock (no real waiting). */
class EmbeddingPacerTest {

    private final List<Duration> waits = new ArrayList<>();
    private long clock;

    private EmbeddingPacer pacer(int rpm, int tpm) {
        return new EmbeddingPacer(rpm, tpm, waits::add, () -> clock);
    }

    @Test
    void smallBatches_AreSpacedOnlyByRequestsPerMinute() {
        EmbeddingPacer pacer = pacer(60, 25_000);
        for (int i = 0; i < 3; i++) {
            pacer.beforeRequest(1_000);
        }
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void tokensPerMinute_HoldsBackABatchUntilTheOldestAgesOut() {
        EmbeddingPacer pacer = pacer(60, 25_000);
        pacer.beforeRequest(10_000); // t = 0
        pacer.beforeRequest(10_000); // t = 1 s (RPM gap)
        pacer.beforeRequest(10_000); // 30K > 25K: waits until the first ages out at t = 60 s

        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(59));
    }

    @Test
    void neverMoreThanTheTokenLimitInAnyMinute() {
        EmbeddingPacer pacer = pacer(60, 25_000);
        List<long[]> sent = new ArrayList<>(); // {time, tokens}
        long virtual = 0;
        for (int i = 0; i < 20; i++) {
            int before = waits.size();
            pacer.beforeRequest(6_000);
            if (waits.size() > before) {
                virtual += waits.get(waits.size() - 1).toNanos();
            }
            sent.add(new long[] {virtual, 6_000});
        }
        for (long[] start : sent) {
            long inWindow = sent.stream().filter(s -> s[0] >= start[0] && s[0] < start[0] + Duration.ofMinutes(1).toNanos())
                    .mapToLong(s -> s[1]).sum();
            assertThat(inWindow).isLessThanOrEqualTo(25_000);
        }
    }

    @Test
    void aBatchBiggerThanTheLimit_IsStillSentAlone() {
        EmbeddingPacer pacer = pacer(60, 25_000);
        pacer.beforeRequest(5_000);
        pacer.beforeRequest(40_000); // can't ever fit: waits for the window to clear, then goes alone

        assertThat(waits).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void realTimePassing_CountsTowardsTheWait() {
        EmbeddingPacer pacer = pacer(60, 25_000);
        pacer.beforeRequest(20_000);
        clock += Duration.ofSeconds(45).toNanos(); // e.g. the previous request took a while
        pacer.beforeRequest(20_000);

        assertThat(waits).containsExactly(Duration.ofSeconds(15));
    }
}
