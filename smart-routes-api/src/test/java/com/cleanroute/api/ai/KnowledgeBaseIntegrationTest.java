package com.cleanroute.api.ai;

import com.cleanroute.api.support.TestDatabase;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The V6 migration and the knowledge base on a throwaway PostGIS + pgvector database (built from
 * db/Dockerfile), with a fake embedding model: no network, no Spring context.
 */
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeBaseIntegrationTest {

    @Container
    static PostgreSQLContainer db = TestDatabase.newContainer();

    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;

    private final List<Duration> waits = new ArrayList<>();

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
    }

    @BeforeEach
    void emptyStore() {
        jdbc.update("DELETE FROM vector_store");
    }

    @Test
    void v6_CreatesTheVectorStoreTableWith768DimsAndAnHnswCosineIndex() {
        String type = jdbc.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'
                """, String.class);
        String index = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'vector_store' AND indexname = 'spring_ai_vector_index'",
                String.class);

        assertThat(type).isEqualTo("vector(768)");
        assertThat(index).containsIgnoringCase("hnsw").contains("vector_cosine_ops");
    }

    @Test
    void ingestingTwice_CreatesNoDuplicatesAndTheSecondRunSendsNoRequests() {
        FakeEmbeddingModel fake = new FakeEmbeddingModel();
        KnowledgeBaseIngestor ingestor = ingestor(fake, "classpath:kb/*.md", 20);

        KnowledgeBaseIngestor.Report first = ingestor.ingest();
        int rows = count();
        int requestsAfterFirst = fake.requests.get();
        KnowledgeBaseIngestor.Report second = ingestor.ingest();

        assertThat(first.ingested()).isEqualTo(5);
        assertThat(rows).isGreaterThanOrEqualTo(5);
        assertThat(second).isEqualTo(new KnowledgeBaseIngestor.Report(0, 5, 0, 0, false));
        assertThat(count()).isEqualTo(rows);
        assertThat(fake.requests.get()).isEqualTo(requestsAfterFirst);
        // Every chunk carries what the Coach needs to cite it, and the model it was embedded with.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM vector_store WHERE metadata->>'title' IS NOT NULL
                  AND metadata->>'source_url' LIKE 'https://%' AND metadata->>'embedding_model' = 'gemini-embedding-001'
                """, Integer.class)).isEqualTo(rows);
        // One request per file (each fits one batch), spaced 2 s apart at 30 requests a minute.
        assertThat(fake.requests.get()).isEqualTo(5);
        assertThat(waits).hasSize(4).allSatisfy(w -> assertThat(w).isLessThanOrEqualTo(Duration.ofSeconds(2)).isPositive());
    }

    @Test
    void rateLimitedMidway_RollsTheWholeFileBack_AndTheNextRunFinishesIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("guide.md");
        Files.writeString(file, document("Version one.", 1));
        ingestor(new FakeEmbeddingModel(), dir.toUri() + "*.md", 1).ingest();
        List<String> before = texts();

        // The new version needs several requests (one chunk per batch); every request after the first hits 429.
        Files.writeString(file, document("Version two.", 6));
        FakeEmbeddingModel limited = new FakeEmbeddingModel(n -> n >= 2 ? tooManyRequests() : null);
        KnowledgeBaseIngestor.Report failed = ingestor(limited, dir.toUri() + "*.md", 1).ingest();

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(failed.stoppedEarly()).isTrue();
        assertThat(texts()).isEqualTo(before); // nothing half-embedded: the old version is untouched

        KnowledgeBaseIngestor.Report retried = ingestor(new FakeEmbeddingModel(), dir.toUri() + "*.md", 1).ingest();
        assertThat(retried.ingested()).isEqualTo(1);
        assertThat(String.join(" ", texts())).contains("Version two.").doesNotContain("Version one.");
    }

    @Test
    void removedFile_ItsChunksAreDeleted(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.md"), document("Alpha.", 1));
        Files.writeString(dir.resolve("b.md"), document("Beta.", 1));
        ingestor(new FakeEmbeddingModel(), dir.toUri() + "*.md", 20).ingest();
        Files.delete(dir.resolve("b.md"));

        KnowledgeBaseIngestor.Report report = ingestor(new FakeEmbeddingModel(), dir.toUri() + "*.md", 20).ingest();

        assertThat(report.removed()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT metadata->>'source_file' FROM vector_store", String.class))
                .containsExactly("a.md");
    }

    @Test
    void searchForSafePm25Level_ReturnsTheWhoGuidelineChunkFirst() {
        FakeEmbeddingModel fake = new FakeEmbeddingModel();
        ingestor(fake, "classpath:kb/*.md", 20).ingest();
        KnowledgeBaseSearch search = search(fake, 0.1);

        List<KnowledgeChunk> hits = search.search("safe PM2.5 level");

        assertThat(hits).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(hits.get(0).sourceFile()).isEqualTo("who-air-quality-guidelines.md");
        assertThat(hits.get(0).text()).contains("15 µg/m³");
        assertThat(hits.get(0).sourceUrl()).startsWith("https://www.who.int/");
        assertThat(hits.get(0).title()).isEqualTo("WHO 2021 air quality guideline levels");
    }

    @Test
    void search_NothingAboveTheThreshold_ReturnsEmpty_AndOtherModelsAreNeverMixedIn() {
        FakeEmbeddingModel fake = new FakeEmbeddingModel();
        ingestor(fake, "classpath:kb/*.md", 20).ingest();
        assertThat(search(fake, 0.99).search("safe PM2.5 level")).isEmpty();
        assertThat(search(fake, 0.1).search("   ")).isEmpty();

        // Rows embedded with a different model are ignored by search, even when they would match.
        jdbc.update("UPDATE vector_store SET metadata = jsonb_set(metadata::jsonb, '{embedding_model}', '\"old-model\"')::json");
        assertThat(search(fake, 0.1).search("safe PM2.5 level")).isEmpty();

        // And the next ingestion re-embeds them with the current model.
        KnowledgeBaseIngestor.Report report = ingestor(fake, "classpath:kb/*.md", 20).ingest();
        assertThat(report.ingested()).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'embedding_model' <> 'gemini-embedding-001'", Integer.class)).isZero();
    }

    @Test
    void search_LogsTheTopScoreEvenBelowTheThreshold_ButNeverTheQuery() {
        FakeEmbeddingModel fake = new FakeEmbeddingModel();
        ingestor(fake, "classpath:kb/*.md", 20).ingest();
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(KnowledgeBaseSearch.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs = new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThat(search(fake, 0.99).search("safe PM2.5 level for my lungs")).isEmpty();
        } finally {
            logger.detachAppender(logs);
        }

        assertThat(logs.list).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).matches("Knowledge base search: top score 0\\.\\d{3}, 0 of 4 results .*0\\.99 threshold");
            assertThat(e.getFormattedMessage()).doesNotContain("PM2.5").doesNotContain("lungs");
        });
    }

    // ------------------------------------------------------------------ helpers

    private KnowledgeBaseIngestor ingestor(FakeEmbeddingModel model, String location, int batchSize) {
        AiProperties settings = AiTestSettings.withKnowledgeBase(0.6, batchSize, 30);
        return new KnowledgeBaseIngestor(settings, embeddings(model, settings), jdbc, transactions, waits::add, location);
    }

    private KnowledgeBaseSearch search(FakeEmbeddingModel model, double threshold) {
        AiProperties settings = AiTestSettings.withKnowledgeBase(threshold, 20, 30);
        return new KnowledgeBaseSearch(settings, embeddings(model, settings), jdbc);
    }

    private EmbeddingService embeddings(FakeEmbeddingModel model, AiProperties settings) {
        return new EmbeddingService(settings, Optional.of(model), Optional.of(model),
                CircuitBreaker.ofDefaults("embedding"), Bulkhead.ofDefaults("embedding"), waits::add);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
    }

    private List<String> texts() {
        return jdbc.queryForList("SELECT content FROM vector_store ORDER BY (metadata->>'chunk_index')::int", String.class);
    }

    /** A kb file whose body is {@code paragraphs} long paragraphs, so it splits into about that many chunks. */
    private static String document(String marker, int paragraphs) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < paragraphs; i++) {
            body.append(marker).append(" Paragraph ").append(i).append(". ")
                    .append("Clean air matters for walking, running and cycling in the city every day. ".repeat(25))
                    .append("\n\n");
        }
        return "---\ntitle: Test guide\nsource_url: https://example.org/guide\n---\n\n" + body;
    }

    private static RuntimeException tooManyRequests() {
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", new org.springframework.http.HttpHeaders(),
                new byte[0], java.nio.charset.StandardCharsets.UTF_8);
    }
}
