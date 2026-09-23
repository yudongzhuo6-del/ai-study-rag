package com.yudong.aistudy.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalEntityMappingTest {

    @Test
    void evalRunFieldsMapToExpectedColumns() throws NoSuchFieldException {
        assertColumn(EvalRun.class, "datasetId", "dataset_id");
        assertColumn(EvalRun.class, "runName", "run_name");
        assertColumn(EvalRun.class, "configSnapshot", "config_snapshot");
        assertColumn(EvalRun.class, "caseCount", "case_count");
        assertColumn(EvalRun.class, "avgRecallAt5", "avg_recall_at_5");
        assertColumn(EvalRun.class, "hitRateAt5", "hit_rate_at_5");
        assertColumn(EvalRun.class, "avgMrr", "avg_mrr");
        assertColumn(EvalRun.class, "citationValidRate", "citation_valid_rate");
        assertColumn(EvalRun.class, "missingCitationRate", "missing_citation_rate");
        assertColumn(EvalRun.class, "avgLatencyMs", "avg_latency_ms");
        assertColumn(EvalRun.class, "avgCorrectnessScore", "avg_correctness_score");
        assertColumn(EvalRun.class, "avgCompletenessScore", "avg_completeness_score");
        assertColumn(EvalRun.class, "avgFaithfulnessScore", "avg_faithfulness_score");
        assertColumn(EvalRun.class, "avgRelevanceScore", "avg_relevance_score");
        assertColumn(EvalRun.class, "avgCitationQualityScore", "avg_citation_quality_score");
        assertColumn(EvalRun.class, "avgJudgeScore", "avg_judge_score");
        assertColumn(EvalRun.class, "createTime", "create_time");
    }

    @Test
    void evalCaseResultFieldsMapToExpectedColumns() throws NoSuchFieldException {
        assertColumn(EvalCaseResult.class, "runId", "run_id");
        assertColumn(EvalCaseResult.class, "caseId", "case_id");
        assertColumn(EvalCaseResult.class, "retrievedChunks", "retrieved_chunks");
        assertColumn(EvalCaseResult.class, "recallAt5", "recall_at_5");
        assertColumn(EvalCaseResult.class, "hitAt5", "hit_at_5");
        assertColumn(EvalCaseResult.class, "citationValid", "citation_valid");
        assertColumn(EvalCaseResult.class, "missingCitation", "missing_citation");
        assertColumn(EvalCaseResult.class, "invalidCitations", "invalid_citations");
        assertColumn(EvalCaseResult.class, "latencyMs", "latency_ms");
        assertColumn(EvalCaseResult.class, "errorMessage", "error_message");
        assertColumn(EvalCaseResult.class, "correctnessScore", "correctness_score");
        assertColumn(EvalCaseResult.class, "completenessScore", "completeness_score");
        assertColumn(EvalCaseResult.class, "faithfulnessScore", "faithfulness_score");
        assertColumn(EvalCaseResult.class, "relevanceScore", "relevance_score");
        assertColumn(EvalCaseResult.class, "citationQualityScore", "citation_quality_score");
        assertColumn(EvalCaseResult.class, "judgeScore", "judge_score");
        assertColumn(EvalCaseResult.class, "judgeReason", "judge_reason");
        assertColumn(EvalCaseResult.class, "judgeRawResponse", "judge_raw_response");
        assertColumn(EvalCaseResult.class, "judgeErrorMessage", "judge_error_message");
        assertColumn(EvalCaseResult.class, "createTime", "create_time");
    }

    @Test
    void evalCaseAndDatasetFieldsMapToExpectedColumns() throws NoSuchFieldException {
        assertColumn(EvalDataset.class, "knowledgeBaseId", "knowledge_base_id");
        assertColumn(EvalDataset.class, "createTime", "create_time");
        assertColumn(EvalCase.class, "datasetId", "dataset_id");
        assertColumn(EvalCase.class, "expectedAnswer", "expected_answer");
        assertColumn(EvalCase.class, "goldenChunks", "golden_chunks");
        assertColumn(EvalCase.class, "requiredKeywords", "required_keywords");
        assertColumn(EvalCase.class, "questionType", "question_type");
        assertColumn(EvalCase.class, "createTime", "create_time");
    }

    private void assertColumn(Class<?> entityClass, String fieldName, String expectedColumn) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        TableField tableField = field.getAnnotation(TableField.class);
        assertEquals(expectedColumn, tableField.value());
    }
}
