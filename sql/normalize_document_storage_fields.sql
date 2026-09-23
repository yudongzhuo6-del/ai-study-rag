-- Run this migration once before starting the updated application.
-- It keeps file_url for legacy compatibility while moving storage metadata
-- into explicitly named columns.

SET @minio_bucket = 'ai-study-rag';

ALTER TABLE document
    ADD COLUMN storage_type VARCHAR(20) NULL COMMENT 'MINIO or LOCAL' AFTER file_url,
    ADD COLUMN object_key VARCHAR(512) NULL COMMENT 'Object key inside the storage bucket' AFTER storage_type,
    ADD COLUMN bucket_name VARCHAR(128) NULL COMMENT 'Object storage bucket name' AFTER object_key,
    ADD COLUMN file_size BIGINT NULL COMMENT 'Original file size in bytes' AFTER bucket_name,
    ADD COLUMN content_type VARCHAR(128) NULL COMMENT 'Original MIME content type' AFTER file_size,
    ADD COLUMN file_hash CHAR(64) NULL COMMENT 'SHA-256 of the original file' AFTER content_type,
    ADD COLUMN process_error VARCHAR(1000) NULL COMMENT 'Last document processing error' AFTER status,
    ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time' AFTER create_time;

ALTER TABLE document
    MODIFY COLUMN file_url VARCHAR(1024) NULL COMMENT 'Legacy file location; use object_key for new records';

-- Documents uploaded by the MinIO integration already store their object key
-- in file_url. Only those records are migrated as MINIO objects.
UPDATE document
SET storage_type = 'MINIO',
    object_key = file_url,
    bucket_name = @minio_bucket
WHERE file_url LIKE 'documents/%';

-- Older absolute paths remain identifiable as local storage records.
UPDATE document
SET storage_type = 'LOCAL'
WHERE storage_type IS NULL
  AND file_url IS NOT NULL
  AND file_url <> '';

CREATE INDEX idx_document_storage_object
    ON document (storage_type, bucket_name, object_key(191));

CREATE INDEX idx_document_file_hash
    ON document (knowledge_base_id, file_hash);
