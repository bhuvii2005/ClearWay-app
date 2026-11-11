package com.cleanroute.api.ai.eval;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The golden set ({@code evals/coach-golden.yaml}) and the retrieval queries ({@code evals/retrieval.yaml}). */
public final class GoldenSet {

    private GoldenSet() {
    }

    public enum Category {
        TOOL_SELECTION("Tool selection", 0.85),
        GROUNDING("Grounding", 0.90),
        KNOWLEDGE_BASE("Knowledge base (citations)", 0.90),
        SAFETY("Safety and security", 1.00);

        public final String label;
        public final double target;

        Category(String label, double target) {
            this.label = label;
            this.target = target;
        }
    }

    /**
     * One case. Text checks are case-insensitive regexes. {@code {placeholders}} in the message and
     * patterns are filled from the seeded data (route ids) before running.
     */
    public record Case(String id, Category category, String user, String message, List<String> expectTools,
                       boolean ordered, List<String> mustContain, List<String> mustNotContain,
                       boolean citationsRequired, boolean grounding, List<String> toolOutputMustContain) {

        public Case withPlaceholders(Map<String, String> values) {
            return new Case(id, category, user, fill(message, values), expectTools, ordered,
                    mustContain.stream().map(p -> fill(p, values)).toList(),
                    mustNotContain.stream().map(p -> fill(p, values)).toList(),
                    citationsRequired, grounding,
                    toolOutputMustContain.stream().map(p -> fill(p, values)).toList());
        }

        private static String fill(String text, Map<String, String> values) {
            String out = text;
            for (var e : values.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue());
            }
            return out;
        }
    }

    public record RetrievalQuery(String query, String expectedFile) {
    }

    public record RetrievalSet(List<RetrievalQuery> queries, List<String> offTopic) {
    }

    public static List<Case> loadCases(String resource) {
        List<Case> cases = new ArrayList<>();
        for (Map<String, Object> m : list(read(resource).get("cases"))) {
            cases.add(new Case(
                    string(m, "id"),
                    Category.valueOf(string(m, "category").toUpperCase(java.util.Locale.ROOT)),
                    m.getOrDefault("user", "main").toString(),
                    string(m, "message"),
                    strings(m.get("expectTools")),
                    Boolean.TRUE.equals(m.get("ordered")),
                    strings(m.get("mustContain")),
                    strings(m.get("mustNotContain")),
                    Boolean.TRUE.equals(m.get("citationsRequired")),
                    Boolean.TRUE.equals(m.get("grounding")),
                    strings(m.get("toolOutputMustContain"))));
        }
        return cases;
    }

    public static RetrievalSet loadRetrieval(String resource) {
        Map<String, Object> root = read(resource);
        List<RetrievalQuery> queries = list(root.get("queries")).stream()
                .map(m -> new RetrievalQuery(string(m, "query"), string(m, "expectedFile"))).toList();
        return new RetrievalSet(queries, strings(root.get("offTopic")));
    }

    private static Map<String, Object> read(String resource) {
        try (InputStream in = GoldenSet.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test resource " + resource);
            }
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<?>) value).stream().map(String::valueOf).toList();
    }

    private static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Golden set entry is missing '" + key + "': " + m);
        }
        return v.toString();
    }
}
