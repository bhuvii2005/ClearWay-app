package com.cleanroute.api.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Metadata keys stored with each knowledge-base chunk, and the one way to open the store.
 * The table itself (vector_store) belongs to Flyway (V6), never to Spring AI.
 */
public final class KnowledgeBaseMetadata {

    public static final String TITLE = "title";
    public static final String SOURCE_URL = "source_url";
    public static final String SOURCE_FILE = "source_file";
    public static final String CONTENT_HASH = "content_hash";
    public static final String EMBEDDING_MODEL = "embedding_model";
    public static final String EMBEDDING_DIMENSIONS = "embedding_dimensions";
    public static final String CHUNK_INDEX = "chunk_index";

    static final String TABLE = "vector_store";

    private KnowledgeBaseMetadata() {
    }

    /** PgVectorStore on the Flyway-owned table: 768-dim cosine HNSW, schema creation off. */
    static PgVectorStore openStore(JdbcTemplate jdbc, EmbeddingModel embeddings, int dimensions) {
        PgVectorStore store = PgVectorStore.builder(jdbc, embeddings)
                .schemaName("public")
                .vectorTableName(TABLE)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(false)
                .build();
        store.afterPropertiesSet();
        return store;
    }
}
