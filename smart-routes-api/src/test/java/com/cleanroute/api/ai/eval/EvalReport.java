package com.cleanroute.api.ai.eval;

import com.cleanroute.api.ai.eval.EvalRunner.CaseResult;
import com.cleanroute.api.ai.eval.EvalRunner.Status;
import com.cleanroute.api.ai.eval.GoldenSet.Category;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Writes {@code build/reports/evals/summary.md}. Pass rates count only cases that ran. */
public final class EvalReport {

    private EvalReport() {
    }

    public record RunInfo(ZonedDateTime startedAt, String provider, String configuredModel, Set<String> servedModels,
                          int chatRequests, long chatTokens, int embeddingRequests, int maxRequests, String preflightNote,
                          String refusal, String stopReason) {
    }

    /**
     * @param lowestExpectedScore the lowest score at which a query's expected file was found (top k, no cutoff)
     * @param highestOffTopicScore the best score any off-topic query reached
     */
    public record Retrieval(int k, List<RetrievalScoring.Row> rows, RetrievalScoring.Row recommended,
                            double currentThreshold, int onTopicQueries, int offTopicQueries,
                            double lowestExpectedScore, double highestOffTopicScore,
                            List<RetrievalScoring.QueryRank> perQuery) {
    }

    public static Path write(Path file, RunInfo info, List<CaseResult> results, Retrieval retrieval) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, render(info, results, retrieval), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String render(RunInfo info, List<CaseResult> results, Retrieval retrieval) {
        StringBuilder md = new StringBuilder();
        md.append("# ClearWay Coach eval summary\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Run at | ").append(info.startedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))).append(" |\n");
        md.append("| Provider | ").append(info.provider()).append(" |\n");
        if (info.maxRequests() == 0) {
            md.append("| Chat requests | none (retrieval-only run) |\n");
        } else {
            md.append("| Configured model | `").append(info.configuredModel()).append("` |\n");
            md.append("| Served model | ").append(info.servedModels().isEmpty() ? "-"
                    : info.servedModels().stream().sorted().map(m -> "`" + m + "`").collect(Collectors.joining(", "))).append(" |\n");
            md.append("| Chat requests | ").append(info.chatRequests()).append(" of ").append(info.maxRequests()).append(" allowed |\n");
            md.append("| Chat tokens | ").append(String.format(Locale.ROOT, "%,d", info.chatTokens())).append(" |\n");
        }
        md.append("| Embedding requests | ").append(info.embeddingRequests()).append(" |\n");
        List<CaseResult> ran = results.stream().filter(r -> r.status() != Status.NOT_RUN).toList();
        if (!ran.isEmpty()) {
            long avg = Math.round(ran.stream().mapToLong(CaseResult::latencyMs).average().orElse(0));
            md.append("| Average latency per message | ").append(String.format(Locale.ROOT, "%.1f s", avg / 1000.0))
                    .append(" (pacing waits excluded) |\n");
        }
        if (info.preflightNote() != null) {
            md.append("| Shared quota check | ").append(info.preflightNote()).append(" |\n");
        }
        md.append('\n');

        if (info.refusal() != null) {
            md.append("**Not run.** ").append(info.refusal()).append("\n");
            return md.toString();
        }
        if (info.stopReason() != null) {
            long notRun = results.stream().filter(r -> r.status() == Status.NOT_RUN).count();
            md.append("**Partial run.** ").append(notRun).append(" case(s) not run: ").append(info.stopReason())
                    .append(". Pass rates below count only the cases that ran.\n\n");
        }

        if (results.isEmpty()) {
            md.append("**Golden set not run** (retrieval-only run).\n");
        } else {
            golden(md, results, ran);
        }
        if (retrieval != null) {
            retrieval(md, retrieval);
        }
        return md.toString();
    }

    private static void golden(StringBuilder md, List<CaseResult> results, List<CaseResult> ran) {
        md.append("## Golden set\n\n");
        md.append("| Category | Passed | Failed | Not run | Pass rate | Target | Met |\n|---|---|---|---|---|---|---|\n");
        for (Category c : Category.values()) {
            List<CaseResult> in = results.stream().filter(r -> r.testCase().category() == c).toList();
            long passed = in.stream().filter(r -> r.status() == Status.PASSED).count();
            long failed = in.stream().filter(r -> r.status() == Status.FAILED).count();
            long notRun = in.stream().filter(r -> r.status() == Status.NOT_RUN).count();
            long total = passed + failed;
            String rate = total == 0 ? "-" : percent((double) passed / total);
            String met = total == 0 ? "-" : (double) passed / total >= c.target ? "yes" : "no";
            md.append("| ").append(c.label).append(" | ").append(passed).append(" | ").append(failed).append(" | ")
                    .append(notRun).append(" | ").append(rate).append(" | ").append(percent(c.target)).append(" | ")
                    .append(met).append(" |\n");
        }
        long passedAll = results.stream().filter(r -> r.status() == Status.PASSED).count();
        md.append("\n**Overall:** ").append(passedAll).append(" of ").append(ran.size()).append(" cases that ran passed.\n\n");

        md.append("## Cases\n\n| Case | Category | Result | Tools used | Requests | Tokens | Latency |\n|---|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            md.append("| ").append(r.testCase().id()).append(" | ").append(r.testCase().category().label).append(" | ")
                    .append(r.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(" | ")
                    .append(r.toolsUsed().isEmpty() ? "-" : String.join(", ", r.toolsUsed())).append(" | ")
                    .append(r.chatRequests()).append(" | ").append(r.tokens()).append(" | ")
                    .append(r.status() == Status.NOT_RUN ? "-" : String.format(Locale.ROOT, "%.1f s", r.latencyMs() / 1000.0))
                    .append(" |\n");
        }

        answers(md, results);
    }

    /**
     * Every case that ran, passed or failed, with its question, the tools called and the answer
     * verbatim, so a run can be audited later without rerunning it.
     */
    private static void answers(StringBuilder md, List<CaseResult> results) {
        md.append("\n## Answers\n\nEvery case that ran, passed or failed. Answers are verbatim.\n");
        for (CaseResult r : results) {
            md.append("\n### ").append(r.testCase().id()).append(" (")
                    .append(r.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(")\n\n");
            if (r.status() == Status.NOT_RUN) {
                md.append("Not run: ").append(oneLine(String.join("; ", r.problems()))).append('\n');
                continue;
            }
            md.append("- **Question:** ").append(oneLine(r.testCase().message())).append('\n');
            md.append("- **Tools called:** ").append(r.toolsUsed().isEmpty() ? "none"
                    : r.toolsUsed().stream().map(t -> "`" + t + "`").collect(Collectors.joining(", "))).append('\n');
            for (String p : r.problems()) {
                md.append("- **Problem:** ").append(oneLine(p)).append('\n');
            }
            if (r.answer() == null) {
                md.append("- **Answer:** none\n");
            } else {
                md.append("- **Answer:**\n\n").append(fenced(r.answer()));
            }
        }
    }

    /** The text in a fenced block, with a fence longer than any backtick run inside it. */
    static String fenced(String text) {
        int longest = 0;
        int run = 0;
        for (char ch : text.toCharArray()) {
            run = ch == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        String body = text.replace("\r\n", "\n").stripTrailing();
        return fence + "text\n" + body + "\n" + fence + "\n";
    }

    private static void retrieval(StringBuilder md, Retrieval retrieval) {
        {
            md.append("\n## Retrieval\n\n");
            md.append(retrieval.onTopicQueries()).append(" queries with an expected source file, and ")
                    .append(retrieval.offTopicQueries()).append(" off-topic queries that should find nothing.\n\n");
            md.append("| Threshold | Recall@").append(retrieval.k()).append(" | MRR | Off-topic false positives |\n|---|---|---|---|\n");
            for (RetrievalScoring.Row row : retrieval.rows()) {
                md.append("| ").append(String.format(Locale.ROOT, "%.2f", row.threshold()))
                        .append(row.threshold() == retrieval.currentThreshold() ? " (current)" : "").append(" | ")
                        .append(percent(row.recallAtK())).append(" | ")
                        .append(String.format(Locale.ROOT, "%.2f", row.mrr())).append(" | ")
                        .append(percent(row.offTopicFalsePositiveRate())).append(" |\n");
            }
            md.append(String.format(Locale.ROOT, "\nScores: the expected file's lowest score was %.3f; the best off-topic score was %.3f.\n",
                    retrieval.lowestExpectedScore(), retrieval.highestOffTopicScore()));
            md.append("\n**Suggested threshold:** ").append(String.format(Locale.ROOT, "%.2f", retrieval.recommended().threshold()))
                    .append(" (the fewest off-topic false positives while recall@").append(retrieval.k())
                    .append(" stays at or above 80%). Not applied automatically.\n");
            if (!retrieval.perQuery().isEmpty()) {
                md.append("\n| Query | Expected file | Rank in top ").append(retrieval.k()).append(" | Score |\n|---|---|---|---|\n");
                for (RetrievalScoring.QueryRank q : retrieval.perQuery()) {
                    md.append("| ").append(oneLine(q.query())).append(" | ").append(q.expectedFile()).append(" | ")
                            .append(q.rank() == 0 ? "not found" : String.valueOf(q.rank())).append(" | ")
                            .append(Double.isNaN(q.score()) ? "-" : String.format(Locale.ROOT, "%.3f", q.score()))
                            .append(" |\n");
                }
            }
        }
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String oneLine(String text) {
        return text.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }
}
