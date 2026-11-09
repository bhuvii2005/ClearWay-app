package com.cleanroute.api.ai;

import com.cleanroute.api.support.DotEnv;
import com.cleanroute.api.support.TestDatabase;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The real knowledge base with real Gemini embeddings (RETRIEVAL_DOCUMENT for the files,
 * RETRIEVAL_QUERY for the questions) in a throwaway database. About 7 embedding requests, inside
 * the eval's own budget and spaced for the eval RPM. Runs only with {@code ./gradlew evalTest},
 * and only when GEMINI_API_KEY is set.
 */
@Tag("eval")
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeBaseLiveTest {

    @Container
    static PostgreSQLContainer db = TestDatabase.newContainer();

    @Test
    void safePm25Level_FindsTheWhoGuideline_AndAnOffTopicQuestionFindsNothing() {
        var key = DotEnv.get("GEMINI_API_KEY");
        assumeTrue(key.isPresent(), "GEMINI_API_KEY not set; skipping the live knowledge-base check");
        EvalSettings eval = EvalSettings.load();

        DriverManagerDataSource ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        AiProperties base = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, key.get(), AiTestSettings.GEMINI_URL, Duration.ofSeconds(30));
        AiProperties settings = new AiProperties(true, base.chat(), base.providers(),
                new AiProperties.Embedding("gemini-embedding-001", 768, Duration.ofSeconds(30), eval.maxRequests),
                new AiProperties.KnowledgeBase(true, 4, base.kb().similarityThreshold(), 300, 20, eval.requestsPerMinute),
                base.limits());
        EmbeddingService embeddings = new EmbeddingService(settings, CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());

        try (AiUsageTally tally = AiUsageTally.open("live knowledge-base eval")) {
            KnowledgeBaseIngestor.Report report = new KnowledgeBaseIngestor(settings, embeddings, jdbc,
                    new DataSourceTransactionManager(ds)).ingest();
            assertThat(report.ingested()).isEqualTo(5);

            KnowledgeBaseSearch search = new KnowledgeBaseSearch(settings, embeddings, jdbc);
            List<KnowledgeChunk> hits = search.search("safe PM2.5 level");
            hits.forEach(h -> System.out.printf("  %.3f  %s%n", h.score(), h.sourceFile()));
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).sourceFile()).isEqualTo("who-air-quality-guidelines.md");

            List<KnowledgeChunk> offTopic = search.search("best pizza toppings in Naples");
            offTopic.forEach(h -> System.out.printf("  off-topic %.3f  %s%n", h.score(), h.sourceFile()));
            assertThat(offTopic).isEmpty();

            System.out.printf("  embedding requests used: %d%n", tally.embeddingRequests());
            assertThat(tally.embeddingRequests()).isLessThanOrEqualTo(eval.maxRequests);
        }
    }
}
