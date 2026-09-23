ALTER TABLE document_chunk
    ADD COLUMN content_hash VARCHAR(64) NULL COMMENT 'chunk content sha256 hash';

CREATE INDEX idx_document_chunk_content_hash
    ON document_chunk (content_hash);

UPDATE document_chunk
SET content_hash = SHA2(TRIM(content), 256)
WHERE content_hash IS NULL
  AND content IS NOT NULL
  AND TRIM(content) <> '';
