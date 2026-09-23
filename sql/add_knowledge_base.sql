CREATE TABLE knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE document
    ADD COLUMN knowledge_base_id BIGINT NULL COMMENT 'Knowledge base id';

CREATE INDEX idx_document_kb_status_type
    ON document (knowledge_base_id, status, type);

CREATE INDEX idx_document_user_kb_status
    ON document (user_id, knowledge_base_id, status);
