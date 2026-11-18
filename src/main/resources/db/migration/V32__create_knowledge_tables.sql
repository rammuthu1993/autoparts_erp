-- ====================================================================
-- Auto Parts ERP AI Assistant: Knowledge Base Tables (Stage 3B)
-- Flyway Migration V32
-- ====================================================================

-- 1. Master Knowledge Documents Table
CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_code VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    document_type VARCHAR(50) NOT NULL
        CHECK (document_type IN ('POLICY', 'MANUAL', 'SOP', 'GUIDELINE', 'HANDBOOK', 'SPECIFICATION', 'FAQ')),
    content_format VARCHAR(20) NOT NULL
        CHECK (content_format IN ('MARKDOWN', 'TEXT', 'PDF')),
    version INTEGER NOT NULL DEFAULT 1
        CHECK (version >= 1),
    required_role VARCHAR(50) NOT NULL DEFAULT 'PUBLIC'
        CHECK (required_role IN ('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER', 'PUBLIC')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    file_name VARCHAR(255) NOT NULL,
    file_hash_sha256 VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL
        CHECK (file_size_bytes > 0),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_knowledge_doc_code_version UNIQUE (document_code, version)
);

-- Partial unique index guaranteeing at most ONE active version per document_code
CREATE UNIQUE INDEX uq_knowledge_documents_single_active_version
    ON knowledge_documents (document_code)
    WHERE active = TRUE;

CREATE INDEX idx_knowledge_docs_lookup
    ON knowledge_documents(document_code, active);

CREATE INDEX idx_knowledge_docs_role
    ON knowledge_documents(required_role, active);


-- 2. Knowledge Chunks Table
CREATE TABLE knowledge_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id UUID NOT NULL
        REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL
        CHECK (chunk_index >= 0),
    section_title VARCHAR(255),
    chunk_text TEXT NOT NULL,
    token_count INTEGER NOT NULL
        CHECK (token_count > 0),
    chunk_hash_sha256 VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Stored generated TSVECTOR for full-text search
    chunk_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(section_title, '') || ' ' || chunk_text)
    ) STORED,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_knowledge_chunk_doc_idx UNIQUE (document_id, chunk_index)
);

-- GIN index for cover-density full-text search
CREATE INDEX idx_knowledge_chunks_tsv
    ON knowledge_chunks USING GIN(chunk_vector);

CREATE INDEX idx_knowledge_chunks_active
    ON knowledge_chunks(active);

CREATE INDEX idx_knowledge_chunks_document
    ON knowledge_chunks(document_id);
