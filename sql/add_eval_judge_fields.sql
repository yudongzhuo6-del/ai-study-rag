ALTER TABLE eval_case_result
    ADD COLUMN correctness_score DOUBLE,
    ADD COLUMN completeness_score DOUBLE,
    ADD COLUMN faithfulness_score DOUBLE,
    ADD COLUMN relevance_score DOUBLE,
    ADD COLUMN citation_quality_score DOUBLE,
    ADD COLUMN judge_score DOUBLE,
    ADD COLUMN judge_reason TEXT,
    ADD COLUMN judge_raw_response TEXT,
    ADD COLUMN judge_error_message TEXT;

ALTER TABLE eval_run
    ADD COLUMN avg_correctness_score DOUBLE,
    ADD COLUMN avg_completeness_score DOUBLE,
    ADD COLUMN avg_faithfulness_score DOUBLE,
    ADD COLUMN avg_relevance_score DOUBLE,
    ADD COLUMN avg_citation_quality_score DOUBLE,
    ADD COLUMN avg_judge_score DOUBLE;
