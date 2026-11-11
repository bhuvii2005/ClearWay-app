package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the Markdown files under {@code classpath:kb/} into the vector store.
 * <ul>
 *   <li>Runs at start-up (in the background) only when {@code clearway.ai.kb.ingest-on-startup=true}
 *       and a Gemini key is set.</li>
 *   <li>A file whose chunks are already stored with the same content hash and embedding model is
 *       skipped, so a normal restart costs no embedding requests.</li>
 *   <li>Each file is replaced in one database transaction: delete its chunks (by the source_file
 *       metadata filter), then add the new ones in batches, one embedding request per batch, spaced
 *       ({@link EmbeddingPacer}: requests and tokens per minute)
 *       for the embedding per-minute limit. If any batch fails (a 429, for example) the whole file
 *       rolls back, so no file is ever half-embedded; ingestion stops and the next start-up retries.</li>
 *   <li>Chunks of files that no longer exist are removed.</li>
 * </ul>
 */
@Component
public class KnowledgeBaseIngestor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);
    private static final String LOCATION = "classpath:kb/*.md";

    /** What one run did. {@code stoppedEarly} means a file failed and the rest wait for the next run. */
    public record Report(int ingested, int unchanged, int removed, int failed, boolean stoppedEarly) {
    }

    private final AiProperties.KnowledgeBase settings;
    private final Optional<VectorStore> store;
    private final String model;
    private final int dimensions;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EmbeddingPacer pacer;
    private final TokenCountEstimator tokens = new JTokkitTokenCountEstimator();
    private final String location;

    @Autowired
    public KnowledgeBaseIngestor(AiProperties properties, EmbeddingService embeddings, JdbcTemplate jdbc,
                                 PlatformTransactionManager transactionManager) {
        this(properties, embeddings, jdbc, transactionManager, Sleeper.REAL, LOCATION);
    }

    KnowledgeBaseIngestor(AiProperties properties, EmbeddingService embeddings, JdbcTemplate jdbc,
                          PlatformTransactionManager transactionManager, Sleeper sleeper, String location) {
        this.settings = properties.kb();
        this.model = embeddings.model();
        this.dimensions = embeddings.dimensions();
        this.store = embeddings.forDocuments().map(gateway -> KnowledgeBaseMetadata.openStore(jdbc, gateway, dimensions));
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.pacer = new EmbeddingPacer(settings.embedRequestsPerMinute(), settings.embedTokensPerMinute(), sleeper,
                System::nanoTime);
        this.location = location;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        if (!settings.ingestOnStartup()) {
            log.info("Knowledge base ingestion on start-up is off (clearway.ai.kb.ingest-on-startup=false).");
            return;
        }
        if (store.isEmpty()) {
            return; // EmbeddingService has already logged why.
        }
        // In the background, so start-up never waits on the embedding provider.
        Thread worker = new Thread(() -> {
            try {
                ingest();
            } catch (RuntimeException e) {
                log.warn("Knowledge base ingestion failed: {}", e.getClass().getSimpleName());
            }
        }, "kb-ingest");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized Report ingest() {
        if (store.isEmpty()) {
            return new Report(0, 0, 0, 0, false);
        }
        List<KnowledgeBaseFile> files = loadFiles();
        Map<String, Set<String>> stored = storedVersions();
        int ingested = 0;
        int unchanged = 0;
        int failed = 0;
        boolean stoppedEarly = false;

        int removed = removeFilesThatAreGone(files, stored.keySet());

        for (KnowledgeBaseFile file : files) {
            String version = version(file.contentHash(settings.chunkSizeTokens(), dimensions));
            if (stored.getOrDefault(file.name(), Set.of()).equals(Set.of(version))) {
                unchanged++;
                continue;
            }
            try (AiUsageTally tally = AiUsageTally.open("knowledge base file " + file.name())) {
                int chunks = replaceFile(file);
                ingested++;
                log.info("Knowledge base: {} stored as {} chunks in {} embedding requests",
                        file.name(), chunks, tally.embeddingRequests());
            } catch (RuntimeException e) {
                failed++;
                stoppedEarly = true;
                log.warn("Knowledge base: {} not stored ({}); nothing of it was changed. The rest waits for the next start-up.",
                        file.name(), e instanceof ApiException ? e.getMessage() : e.getClass().getSimpleName());
                break;
            }
        }
        Report report = new Report(ingested, unchanged, removed, failed, stoppedEarly);
        log.info("Knowledge base ingestion: {}", report);
        return report;
    }

    /** Delete then add, in one transaction: a failure anywhere leaves the file exactly as it was. */
    private int replaceFile(KnowledgeBaseFile file) {
        List<Document> chunks = split(file);
        transaction.executeWithoutResult(status -> {
            store.get().delete(new FilterExpressionBuilder().eq(KnowledgeBaseMetadata.SOURCE_FILE, file.name()).build());
            for (int from = 0; from < chunks.size(); from += settings.batchSize()) {
                List<Document> batch = chunks.subList(from, Math.min(chunks.size(), from + settings.batchSize()));
                pacer.beforeRequest(batch.stream().mapToLong(d -> tokens.estimate(d.getText())).sum());
                store.get().add(batch);
            }
        });
        return chunks.size();
    }

    List<Document> split(KnowledgeBaseFile file) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(settings.chunkSizeTokens())
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(20)
                .build();
        List<Document> pieces = splitter.apply(List.of(new Document(file.body())));
        String hash = file.contentHash(settings.chunkSizeTokens(), dimensions);
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(KnowledgeBaseMetadata.TITLE, file.title());
            meta.put(KnowledgeBaseMetadata.SOURCE_URL, file.sourceUrl());
            meta.put(KnowledgeBaseMetadata.SOURCE_FILE, file.name());
            meta.put(KnowledgeBaseMetadata.CONTENT_HASH, hash);
            meta.put(KnowledgeBaseMetadata.EMBEDDING_MODEL, model);
            meta.put(KnowledgeBaseMetadata.EMBEDDING_DIMENSIONS, dimensions);
            meta.put(KnowledgeBaseMetadata.CHUNK_INDEX, i);
            chunks.add(new Document(pieces.get(i).getText(), meta));
        }
        return chunks;
    }

    private int removeFilesThatAreGone(List<KnowledgeBaseFile> files, Set<String> storedFiles) {
        Set<String> current = new HashSet<>();
        files.forEach(f -> current.add(f.name()));
        int removed = 0;
        for (String name : storedFiles) {
            if (!current.contains(name)) {
                transaction.executeWithoutResult(status -> store.get().delete(
                        new FilterExpressionBuilder().eq(KnowledgeBaseMetadata.SOURCE_FILE, name).build()));
                log.info("Knowledge base: removed chunks of {}, which is no longer in kb/", name);
                removed++;
            }
        }
        return removed;
    }

    /** source_file -> the distinct (hash, model) versions stored for it. */
    private Map<String, Set<String>> storedVersions() {
        Map<String, Set<String>> versions = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT metadata->>'source_file' AS file, metadata->>'content_hash' AS hash,
                       metadata->>'embedding_model' AS model
                FROM vector_store WHERE metadata->>'source_file' IS NOT NULL
                """, rs -> {
            versions.computeIfAbsent(rs.getString("file"), k -> new HashSet<>())
                    .add(rs.getString("hash") + "@" + rs.getString("model"));
        });
        versions.forEach((file, v) -> {
            if (v.stream().anyMatch(s -> !s.endsWith("@" + model))) {
                log.info("Knowledge base: {} was embedded with another model; it will be re-embedded with {}", file, model);
            }
        });
        return versions;
    }

    private String version(String hash) {
        return hash + "@" + model;
    }

    private List<KnowledgeBaseFile> loadFiles() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
            List<KnowledgeBaseFile> files = new ArrayList<>();
            for (Resource resource : resources) {
                String name = resource.getFilename();
                try {
                    files.add(KnowledgeBaseFile.parse(name, resource.getContentAsString(StandardCharsets.UTF_8)));
                } catch (IllegalArgumentException e) {
                    log.error("Knowledge base: skipping {}", e.getMessage());
                }
            }
            files.sort(Comparator.comparing(KnowledgeBaseFile::name));
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the knowledge base files", e);
        }
    }
}
