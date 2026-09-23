ALTER TABLE document
    ADD COLUMN user_id BIGINT NULL COMMENT 'Owner user id';

CREATE INDEX idx_document_user_status_type
    ON document (user_id, status, type);
