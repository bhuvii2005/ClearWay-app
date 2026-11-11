package com.cleanroute.api.ai.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Retrieval metrics at several similarity thresholds, from each query's top-k results (no
 * threshold applied when searching, so one search per query serves every threshold).
 */
public final class RetrievalScoring {

    private RetrievalScoring() {
    }

    public record Hit(String sourceFile, double score) {
    }

    /** One on-topic query: its expected file and the ranked results (best first). */
    public record Ranked(String query, String expectedFile, List<Hit> hits) {
    }

    public record Row(double threshold, double recallAtK, double mrr, double offTopicFalsePositiveRate) {
    }

    public static List<Row> table(List<Ranked> onTopic, List<List<Hit>> offTopic, int k, List<Double> thresholds) {
        List<Row> rows = new ArrayList<>();
        for (double t : thresholds) {
            double recall = 0;
            double rr = 0;
            for (Ranked q : onTopic) {
                List<Hit> kept = q.hits().stream().filter(h -> h.score() >= t).limit(k).toList();
                for (int i = 0; i < kept.size(); i++) {
                    if (kept.get(i).sourceFile().equals(q.expectedFile())) {
                        recall++;
                        rr += 1.0 / (i + 1);
                        break;
                    }
                }
            }
            long falsePositives = offTopic.stream().filter(hits -> hits.stream().anyMatch(h -> h.score() >= t)).count();
            int n = Math.max(1, onTopic.size());
            rows.add(new Row(t, recall / n, rr / n, offTopic.isEmpty() ? 0 : (double) falsePositives / offTopic.size()));
        }
        return rows;
    }

    /** The lowest score at which each query's expected file appears (its best hit); NaN if none. */
    public static double lowestExpectedScore(List<Ranked> onTopic) {
        return onTopic.stream().mapToDouble(q -> q.hits().stream().filter(h -> h.sourceFile().equals(q.expectedFile()))
                .mapToDouble(Hit::score).max().orElse(Double.NaN)).min().orElse(Double.NaN);
    }

    public static double highestScore(List<List<Hit>> results) {
        return results.stream().flatMap(List::stream).mapToDouble(Hit::score).max().orElse(Double.NaN);
    }

    /**
     * The threshold to suggest: among those reaching the recall target, the one with the fewest
     * off-topic false positives (the higher threshold on a tie). If none reaches it, the best recall.
     */
    public static Row recommend(List<Row> rows, double recallTarget) {
        List<Row> meeting = rows.stream().filter(r -> r.recallAtK() >= recallTarget).toList();
        if (!meeting.isEmpty()) {
            return meeting.stream()
                    .min(Comparator.comparingDouble(Row::offTopicFalsePositiveRate)
                            .thenComparing(Comparator.comparingDouble(Row::threshold).reversed()))
                    .orElseThrow();
        }
        return rows.stream().max(Comparator.comparingDouble(Row::recallAtK)
                .thenComparingDouble(r -> -r.offTopicFalsePositiveRate())).orElseThrow();
    }
}
