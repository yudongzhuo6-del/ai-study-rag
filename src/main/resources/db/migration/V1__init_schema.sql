SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE knowledge_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_knowledge_base_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    file_url VARCHAR(1024) NULL COMMENT 'Legacy file location; use object_key for new records',
    storage_type VARCHAR(20) NULL COMMENT 'MINIO or LOCAL',
    object_key VARCHAR(512) NULL COMMENT 'Object key inside the storage bucket',
    bucket_name VARCHAR(128) NULL COMMENT 'Object storage bucket name',
    file_size BIGINT NULL COMMENT 'Original file size in bytes',
    content_type VARCHAR(128) NULL COMMENT 'Original MIME content type',
    file_hash CHAR(64) NULL COMMENT 'SHA-256 of the original file',
    type VARCHAR(50) NULL,
    user_id BIGINT NULL COMMENT 'Owner user id',
    knowledge_base_id BIGINT NULL COMMENT 'Knowledge base id',
    status TINYINT NOT NULL DEFAULT 0,
    process_error VARCHAR(1000) NULL COMMENT 'Last document processing error',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_document_user_status_type (user_id, status, type),
    INDEX idx_document_kb_status_type (knowledge_base_id, status, type),
    INDEX idx_document_user_kb_status (user_id, knowledge_base_id, status),
    INDEX idx_document_storage_object (storage_type, bucket_name, object_key(191)),
    UNIQUE KEY uk_document_file_hash (knowledge_base_id, file_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE document_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    chunk_index INT NOT NULL,
    content_hash VARCHAR(64) NULL COMMENT 'Chunk content SHA-256 hash',
    vector_id VARCHAR(128) NULL,
    PRIMARY KEY (id),
    INDEX idx_document_chunk_document_id (document_id),
    INDEX idx_document_chunk_content_hash (content_hash),
    UNIQUE KEY uk_document_chunk_index (document_id, chunk_index)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chat_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chat_session_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer LONGTEXT NULL,
    source LONGTEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chat_message_session_id (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_dataset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    knowledge_base_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_eval_dataset_knowledge_base_id (knowledge_base_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    expected_answer TEXT NULL,
    golden_chunks JSON NULL,
    required_keywords JSON NULL,
    question_type VARCHAR(64) NULL,
    difficulty VARCHAR(32) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_eval_case_dataset_id (dataset_id),
    INDEX idx_eval_case_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    run_name VARCHAR(128) NULL,
    config_snapshot JSON NULL,
    case_count INT NOT NULL DEFAULT 0,
    avg_recall_at_5 DOUBLE NULL,
    hit_rate_at_5 DOUBLE NULL,
    avg_mrr DOUBLE NULL,
    citation_valid_rate DOUBLE NULL,
    missing_citation_rate DOUBLE NULL,
    avg_latency_ms DOUBLE NULL,
    avg_correctness_score DOUBLE NULL,
    avg_completeness_score DOUBLE NULL,
    avg_faithfulness_score DOUBLE NULL,
    avg_relevance_score DOUBLE NULL,
    avg_citation_quality_score DOUBLE NULL,
    avg_judge_score DOUBLE NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_eval_run_dataset_id (dataset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_case_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    answer LONGTEXT NULL,
    retrieved_chunks JSON NULL,
    recall_at_5 DOUBLE NULL,
    hit_at_5 TINYINT NULL,
    mrr DOUBLE NULL,
    citation_valid TINYINT NULL,
    missing_citation TINYINT NULL,
    invalid_citations JSON NULL,
    latency_ms BIGINT NULL,
    error_message TEXT NULL,
    correctness_score DOUBLE NULL,
    completeness_score DOUBLE NULL,
    faithfulness_score DOUBLE NULL,
    relevance_score DOUBLE NULL,
    citation_quality_score DOUBLE NULL,
    judge_score DOUBLE NULL,
    judge_reason TEXT NULL,
    judge_raw_response LONGTEXT NULL,
    judge_error_message TEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_eval_case_result_run_id (run_id),
    INDEX idx_eval_case_result_case_id (case_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
