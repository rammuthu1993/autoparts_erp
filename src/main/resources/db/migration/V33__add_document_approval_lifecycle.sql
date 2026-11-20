-- ====================================================================
-- Auto Parts ERP AI Assistant: Document Approval Lifecycle (Stage 3C)
-- Flyway Migration V33
-- ====================================================================

ALTER TABLE knowledge_documents
    ADD COLUMN approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    ADD COLUMN approved_by VARCHAR(150),
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN rejected_by VARCHAR(150),
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN effective_from TIMESTAMPTZ,
    ADD COLUMN effective_until TIMESTAMPTZ;

-- All legacy documents explicitly remain PENDING_APPROVAL until human review.
-- Ingestion and migrations never automatically approve unreviewed documents.

ALTER TABLE knowledge_documents
    ADD CONSTRAINT chk_knowledge_docs_approval_status
        CHECK (approval_status IN ('APPROVED', 'PENDING_APPROVAL', 'REJECTED'));

CREATE INDEX idx_knowledge_docs_approval
    ON knowledge_documents(approval_status, active, effective_from, effective_until);
