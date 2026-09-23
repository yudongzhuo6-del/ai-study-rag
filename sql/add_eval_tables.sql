CREATE TABLE IF NOT EXISTS eval_dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    knowledge_base_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    expected_answer TEXT,
    golden_chunks JSON,
    required_keywords JSON,
    question_type VARCHAR(64),
    difficulty VARCHAR(32),
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_case_dataset_id (dataset_id),
    INDEX idx_eval_case_enabled (enabled)
);

CREATE TABLE IF NOT EXISTS eval_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    run_name VARCHAR(128),
    config_snapshot JSON,
    case_count INT DEFAULT 0,
    avg_recall_at_5 DOUBLE,
    hit_rate_at_5 DOUBLE,
    avg_mrr DOUBLE,
    citation_valid_rate DOUBLE,
    missing_citation_rate DOUBLE,
    avg_latency_ms DOUBLE,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_run_dataset_id (dataset_id)
);

CREATE TABLE IF NOT EXISTS eval_case_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    answer TEXT,
    retrieved_chunks JSON,
    recall_at_5 DOUBLE,
    hit_at_5 TINYINT,
    mrr DOUBLE,
    citation_valid TINYINT,
    missing_citation TINYINT,
    invalid_citations JSON,
    latency_ms BIGINT,
    error_message TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_case_result_run_id (run_id),
    INDEX idx_eval_case_result_case_id (case_id)
);
