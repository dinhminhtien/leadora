-- ============================================================================
-- Internal Sales Role-Based Chat Assistant — schema (feature/ai_internal)
-- ============================================================================
-- With SPRING_JPA_DDL_AUTO=update Hibernate auto-creates `ai_documents`, and Spring AI
-- (spring.ai.vectorstore.pgvector.initialize-schema=true) auto-creates the vector store.
-- Run this script manually only if you switch ddl-auto to `validate` or initialize-schema=false,
-- or to provision the schema ahead of time (e.g. on Supabase).
-- ============================================================================

-- pgvector extension (required by the vector store).
CREATE EXTENSION IF NOT EXISTS vector;

-- Metadata for ingested company documents (the chunks/embeddings live in the vector store).
CREATE TABLE IF NOT EXISTS ai_documents (
    document_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    file_name    VARCHAR(255),
    content_type VARCHAR(100),
    chunk_count  INTEGER      NOT NULL DEFAULT 0,
    uploaded_by  UUID REFERENCES users(user_id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Vector store (normally created automatically by Spring AI). Provided for the
-- manual path. Dimension 768 matches the Gemini `text-embedding-004` model — change
-- it if you use a different GEMINI_EMBEDDING_MODEL (and AI_EMBEDDING_DIMENSIONS).
--
-- ⚠️ If you switched embedding models, the dimension changed. Spring AI will NOT
--    alter an existing table, so drop it once and let it be recreated:
--        DROP TABLE IF EXISTS public.leadora_vector_store;
-- ----------------------------------------------------------------------------
-- CREATE EXTENSION IF NOT EXISTS hstore;
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE TABLE IF NOT EXISTS public.leadora_vector_store (
--     id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
--     content   TEXT,
--     metadata  JSON,
--     embedding VECTOR(768)
-- );
-- CREATE INDEX IF NOT EXISTS leadora_vector_store_hnsw_idx
--     ON public.leadora_vector_store USING HNSW (embedding vector_cosine_ops);
