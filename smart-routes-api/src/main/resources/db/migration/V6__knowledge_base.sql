-- Knowledge base for the ClearWay Coach: the table Spring AI's PgVectorStore expects, as documented
-- for Spring AI 2.0 (PGvector page), at 768 dimensions (gemini-embedding-001) with an HNSW cosine index.
-- Flyway owns this schema: the store runs with initialize-schema off. Hibernate does not map it.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(768)
);

-- Cosine distance: gemini-embedding-001 vectors below 3072 dimensions are not normalised.
CREATE INDEX spring_ai_vector_index ON vector_store USING HNSW (embedding vector_cosine_ops);
