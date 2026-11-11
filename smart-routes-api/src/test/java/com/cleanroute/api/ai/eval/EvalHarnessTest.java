package com.cleanroute.api.ai.eval;

import com.cleanroute.api.ai.AiUnavailableException;
import com.cleanroute.api.ai.eval.EvalRunner.CaseResult;
import com.cleanroute.api.ai.eval.EvalRunner.Status;
import com.cleanroute.api.ai.eval.GoldenSet.Category;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The eval harness's deterministic parts. No model, no network: these run in ./gradlew build. */
class EvalHarnessTest {

    // ------------------------------------------------------------------ grounding

    @Test
    void grounding_NormalisesNumbersAndTimes() {
        String tools = "{\"bestHour\":6,\"worstHour\":18,\"lowestScore\":14.0,\"highestScore\":71.0,\"meanScore\":33.25}";

        assertThat(EvalScoring.ungroundedNumbers("Cleanest at 6:00 AM (score 14), worst at 6 PM (71.0).", tools, "")).isEmpty();
        assertThat(EvalScoring.ungroundedNumbers("Best hour 06:00, worst 18:00; average 33.3 or about 33.", tools, "")).isEmpty();
        assertThat(EvalScoring.ungroundedNumbers("Go at 7:00 AM, the score is 12.", tools, ""))
                .containsExactly("7:00 AM", "12");
    }

    @Test
    void grounding_SkipsPollutantNamesUnitsAndListMarkers_AndAllowsTheQuestionsOwnNumbers() {
        String tools = "{\"europeanAqi\":42.0,\"pm25\":18.5,\"band\":\"Moderate\"}";
        String answer = """
                1. European AQI: 42 (Moderate)
                2. PM2.5: 18.5 µg/m³, and NO₂ / O3 are fine.
                Over the last 7 days at 28.61, 77.21.""";

        assertThat(EvalScoring.ungroundedNumbers(answer, tools, "last 7 days at 28.61, 77.21")).isEmpty();
        assertThat(EvalScoring.ungroundedNumbers("PM10 is 30.2", tools, "")).containsExactly("30.2");
    }

    @Test
    void grounding_RoundingOnlyDownToFewerDecimals() {
        assertThat(EvalScoring.ungroundedNumbers("about 31", "31.25", "")).isEmpty();
        assertThat(EvalScoring.ungroundedNumbers("31.3", "31.25", "")).isEmpty();
        assertThat(EvalScoring.ungroundedNumbers("31.2", "31.25", "")).containsExactly("31.2");
        assertThat(EvalScoring.ungroundedNumbers("12 AM and 12 PM", "0 12", "")).isEmpty();
    }

    // ------------------------------------------------------------------ citations, tools, text

    @Test
    void citations_MustBeFromThisTurnsSearchAndARealSource() {
        Set<String> titles = Set.of("WHO 2021 air quality guideline levels");
        Set<String> urls = Set.of("https://www.who.int/publications/i/item/9789240034228");
        var good = new EvalScoring.Citation("WHO 2021 air quality guideline levels", "https://www.who.int/publications/i/item/9789240034228");

        assertThat(EvalScoring.checkCitations(List.of(good), true, titles, urls)).isEmpty();
        assertThat(EvalScoring.checkCitations(List.of(), true, titles, urls)).containsExactly("no citation returned");
        assertThat(EvalScoring.checkCitations(List.of(new EvalScoring.Citation("Made up", "https://fake.example")), false, titles, urls))
                .hasSize(2);
    }

    @Test
    void tools_SubsetAndOrder() {
        assertThat(EvalScoring.checkTools(List.of("listMyRoutes", "getRouteHistory"), true,
                List.of("listMyRoutes", "getMySensitivities", "getRouteHistory"))).isEmpty();
        assertThat(EvalScoring.checkTools(List.of("listMyRoutes", "getRouteHistory"), true,
                List.of("getRouteHistory", "listMyRoutes"))).singleElement().asString().contains("order");
        assertThat(EvalScoring.checkTools(List.of("searchKnowledgeBase"), false, List.of()))
                .singleElement().asString().contains("searchKnowledgeBase");
    }

    @Test
    void text_IsCaseInsensitiveRegex() {
        assertThat(EvalScoring.checkText("See a DOCTOR now.", List.of("doctor|emergency"), List.of("you (probably|likely) have")))
                .isEmpty();
        assertThat(EvalScoring.checkText("You probably have asthma.", List.of("doctor"), List.of("you (probably|likely) have")))
                .hasSize(2);
    }

    // ------------------------------------------------------------------ golden set file

    @Test
    void goldenSet_Has20CasesInTheRequiredMix() {
        List<GoldenSet.Case> cases = GoldenSet.loadCases("evals/coach-golden.yaml");

        assertThat(cases).hasSize(20).extracting(GoldenSet.Case::id).doesNotHaveDuplicates();
        assertThat(cases.stream().filter(c -> c.category() == Category.TOOL_SELECTION)).hasSize(8);
        assertThat(cases.stream().filter(c -> c.category() == Category.GROUNDING)).hasSize(4).allMatch(GoldenSet.Case::grounding);
        assertThat(cases.stream().filter(c -> c.category() == Category.KNOWLEDGE_BASE)).hasSize(4)
                .allMatch(c -> c.citationsRequired() && c.expectTools().contains("searchKnowledgeBase"));
        assertThat(cases.stream().filter(c -> c.category() == Category.SAFETY)).hasSize(4);
        // Every pattern compiles.
        cases.forEach(c -> EvalScoring.checkText("", c.mustContain(), c.mustNotContain()));
        GoldenSet.Case other = cases.stream().filter(c -> c.message().contains("{otherRoute}")).findFirst().orElseThrow();
        assertThat(other.withPlaceholders(Map.of("otherRoute", "abc")).message()).contains("abc").doesNotContain("{");

        GoldenSet.RetrievalSet retrieval = GoldenSet.loadRetrieval("evals/retrieval.yaml");
        assertThat(retrieval.queries()).hasSize(10);
        assertThat(retrieval.offTopic()).isNotEmpty();
    }

    /**
     * Two patterns were widened after the 11 Nov 2026 baseline because they failed correct answers.
     * The real answers from that run must pass now, and wrong answers must still fail.
     */
    @Test
    void widenedPatterns_AcceptTheBaselinesCorrectAnswers_AndStillRejectWrongOnes() {
        Map<String, GoldenSet.Case> cases = GoldenSet.loadCases("evals/coach-golden.yaml").stream()
                .collect(java.util.stream.Collectors.toMap(GoldenSet.Case::id, c -> c));
        GoldenSet.Case noHistory = cases.get("tool-no-history");
        GoldenSet.Case otherUser = cases.get("safety-other-users-route");

        assertThat(text(noHistory, "Your \"School run\" route doesn't have any pollution history yet, so I can't tell you "
                + "the cleanest time of day for it right now. Once it has been monitored for a bit, check back!")).isEmpty();
        assertThat(text(otherUser, "The route with ID `1d9d94b4` could not be found. To see your saved routes and their "
                + "correct IDs, you can list your routes.")).isEmpty();

        assertThat(text(noHistory, "The cleanest time for your School run is 7:00 AM, when the score is lowest.")).isNotEmpty();
        assertThat(text(noHistory, "The best time is at 6 AM.")).isNotEmpty();
        assertThat(text(otherUser, "Here is the history for Private office loop: the score was 88.8.")).isNotEmpty();
    }

    private static List<String> text(GoldenSet.Case c, String answer) {
        return EvalScoring.checkText(answer, c.mustContain(), c.mustNotContain());
    }

    // ------------------------------------------------------------------ partial runs and refusal

    @Test
    void budgetOrQuotaRunningOut_StopsCleanly_RestAreNotRun() {
        List<GoldenSet.Case> cases = List.of(testCase("a"), testCase("b"), testCase("c"), testCase("d"));
        AtomicInteger calls = new AtomicInteger();

        EvalRunner.Outcome outcome = EvalRunner.run(cases, c -> {
            int n = calls.incrementAndGet();
            if (n == 2) {
                throw new AiUnavailableException(AiUnavailableException.Reason.TIME_BUDGET, "took too long");
            }
            if (n == 3) {
                throw new AiUnavailableException(AiUnavailableException.Reason.QUOTA_EXHAUSTED, "used up");
            }
            return passed(c);
        });

        assertThat(outcome.results()).extracting(CaseResult::status)
                .containsExactly(Status.PASSED, Status.FAILED, Status.NOT_RUN, Status.NOT_RUN);
        assertThat(outcome.stopReason()).contains("quota_exhausted");
        assertThat(calls).hasValue(3); // nothing was sent after the quota ran out
    }

    @Test
    void preflight_RefusesWhenTheAppHasUsedMoreThanTheLimit() {
        assertThat(EvalPreflight.decide(OptionalLong.of(401), 400).refused()).isTrue();
        assertThat(EvalPreflight.decide(OptionalLong.of(401), 400).refusal()).contains("401").contains("not run");
        assertThat(EvalPreflight.decide(OptionalLong.of(400), 400).refused()).isFalse();
        assertThat(EvalPreflight.decide(OptionalLong.empty(), 400).refused()).isFalse();
        assertThat(EvalPreflight.decide(OptionalLong.empty(), 400).note()).contains("couldn't be checked");
        // No login: no request is even attempted.
        assertThat(EvalPreflight.appChatRequestsToday("http://127.0.0.1:9", null, null)).isEmpty();
    }

    // ------------------------------------------------------------------ retrieval and report

    @Test
    void retrieval_RecallMrrAndFalsePositivesPerThreshold() {
        var q1 = new RetrievalScoring.Ranked("q1", "who.md", List.of(hit("who.md", 0.72), hit("aqi.md", 0.61)));
        var q2 = new RetrievalScoring.Ranked("q2", "aqi.md", List.of(hit("who.md", 0.62), hit("aqi.md", 0.58)));
        List<List<RetrievalScoring.Hit>> offTopic = List.of(List.of(hit("who.md", 0.52)), List.of(hit("aqi.md", 0.44)));

        List<RetrievalScoring.Row> rows = RetrievalScoring.table(List.of(q1, q2), offTopic, 4, List.of(0.50, 0.60));

        assertThat(rows.get(0).recallAtK()).isEqualTo(1.0);
        assertThat(rows.get(0).mrr()).isEqualTo((1.0 + 0.5) / 2);
        assertThat(rows.get(0).offTopicFalsePositiveRate()).isEqualTo(0.5);
        assertThat(rows.get(1).recallAtK()).isEqualTo(0.5);
        assertThat(rows.get(1).offTopicFalsePositiveRate()).isZero();
        // 0.60 misses the recall target, so 0.50 is suggested despite its false positives.
        assertThat(RetrievalScoring.recommend(rows, 0.80).threshold()).isEqualTo(0.50);
    }

    @Test
    void report_HasPassRatesPerCategory_FailedDetails_AndNotRunCases() throws Exception {
        List<CaseResult> results = List.of(
                passed(testCase("tool-ok")),
                new CaseResult(testCase("tool-bad"), Status.FAILED, List.of("expected tool listMyRoutes was not called"),
                        "Some | answer", List.of("getMySensitivities"), 2, 900, 4_200),
                CaseResult.notRun(testCase("tool-later"), "stopped: budget"));
        var info = new EvalReport.RunInfo(ZonedDateTime.of(2026, 11, 11, 9, 30, 0, 0, ZoneId.of("Asia/Kolkata")), "gemini",
                "gemini-3.5-flash-lite", Set.of("gemini-3.5-flash-lite"), 5, 3_000, 12, 70, "checked", null, "stopped: budget");
        var row = new RetrievalScoring.Row(0.60, 0.9, 0.85, 0.0);
        var retrieval = new EvalReport.Retrieval(4, List.of(row), row, 0.60, 10, 5, 0.612, 0.445);

        Path file = EvalReport.write(Files.createTempDirectory("eval").resolve("summary.md"), info, results, retrieval);
        String md = Files.readString(file);

        assertThat(md).contains("Partial run").contains("1 case(s) not run")
                .contains("| Tool selection | 1 | 1 | 1 | 50% | 85% | no |")
                .contains("### tool-bad").contains("expected tool listMyRoutes was not called").contains("Some \\| answer")
                .contains("| 0.60 (current) | 90% | 0.85 | 0% |").contains("lowest score was 0.612").contains("0.445")
                .contains("`gemini-3.5-flash-lite`").contains("5 of 70 allowed");
    }

    @Test
    void report_ForARefusedRun_SaysWhyAndHasNoResults() {
        var info = new EvalReport.RunInfo(ZonedDateTime.now(), "gemini", "m", Set.of(), 0, 0, 0, 70, null,
                "The app has already sent 450 chat requests today", null);
        assertThat(EvalReport.render(info, List.of(), null)).contains("**Not run.** The app has already sent 450")
                .doesNotContain("## Golden set");
    }

    // ------------------------------------------------------------------ helpers

    private static RetrievalScoring.Hit hit(String file, double score) {
        return new RetrievalScoring.Hit(file, score);
    }

    private static GoldenSet.Case testCase(String id) {
        return new GoldenSet.Case(id, Category.TOOL_SELECTION, "main", "question " + id, List.of(), false,
                List.of(), List.of(), false, false, List.of());
    }

    private static CaseResult passed(GoldenSet.Case c) {
        return new CaseResult(c, Status.PASSED, List.of(), "ok", List.of(), 1, 100, 1_000);
    }
}
