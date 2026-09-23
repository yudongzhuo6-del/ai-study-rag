-- Run after normalize_document_storage_fields.sql.
-- Verify and resolve duplicate non-null hashes before applying this migration.

DROP INDEX idx_document_file_hash ON document;

CREATE UNIQUE INDEX uk_document_file_hash
    ON document (knowledge_base_id, file_hash);
