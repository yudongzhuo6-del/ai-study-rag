package com.yudong.aistudy.service.impl;

import com.yudong.aistudy.model.entity.EvalCaseResult;
import com.yudong.aistudy.model.entity.EvalRun;
import com.yudong.aistudy.model.vo.eval.EvalRunDiagnosisVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalDiagnosisServiceImplTest {

    private final EvalDiagnosisServiceImpl service = new EvalDiagnosisServiceImpl(null, null);

    @Test
    void diagnosePrioritizesRetrievalRecallWhenGoldenChunksAreMissed() {
        EvalRun run = run(1L);
        run.setHitRateAt5(0.25);
        run.setAvgRecallAt5(0.25);
        run.setAvgMrr(0.1);
        run.setCitationValidRate(1.0);
        run.setMissingCitationRate(0.0);
        run.setAvgLatencyMs(1200.0);

        EvalRunDiagnosisVO diagnosis = service.diagnose(run, List.of(
                result(1L, 0, 0.0, 0.0),
                result(2L, 0, 0.0, 0.0),
                result(3L, 1, 1.0, 1.0),
                result(4L, 0, 0.0, 0.0)
        ));

        assertEquals("RETRIEVAL_RECALL", diagnosis.getPrimaryBottleneck());
        assertEquals("HIGH", diagnosis.getItems().get(0).getPriority());
        assertEquals(List.of(1L, 2L, 4L), diagnosis.getItems().get(0).getEvidenceCaseIds());
    }

    @Test
    void diagnosePrioritizesGenerationWhenRetrievalIsGoodButCorrectnessIsLow() {
        EvalRun run = run(2L);
        run.setHitRateAt5(1.0);
        run.setAvgRecallAt5(1.0);
        run.setAvgMrr(1.0);
        run.setCitationValidRate(1.0);
        run.setMissingCitationRate(0.0);
        run.setAvgLatencyMs(1000.0);

        EvalCaseResult first = result(10L, 1, 1.0, 1.0);
        first.setCorrectnessScore(2.0);
        first.setFaithfulnessScore(5.0);
        EvalCaseResult second = result(11L, 1, 1.0, 1.0);
        second.setCorrectnessScore(2.5);
        second.setFaithfulnessScore(4.0);

        EvalRunDiagnosisVO diagnosis = service.diagnose(run, List.of(first, second));

        assertEquals("GENERATION", diagnosis.getPrimaryBottleneck());
        assertEquals(List.of(10L, 11L), diagnosis.getItems().get(0).getEvidenceCaseIds());
    }

    @Test
    void diagnoseFindsCitationProblemsFromRuleAndJudgeSignals() {
        EvalRun run = run(3L);
        run.setHitRateAt5(1.0);
        run.setAvgRecallAt5(1.0);
        run.setAvgMrr(1.0);
        run.setCitationValidRate(0.0);
        run.setMissingCitationRate(0.5);
        run.setAvgLatencyMs(1000.0);

        EvalCaseResult first = result(20L, 1, 1.0, 1.0);
        first.setCitationValid(0);
        first.setMissingCitation(1);
        first.setInvalidCitations("[]");
        EvalCaseResult second = result(21L, 1, 1.0, 1.0);
        second.setCitationValid(0);
        second.setMissingCitation(0);
        second.setInvalidCitations("[5]");

        EvalRunDiagnosisVO diagnosis = service.diagnose(run, List.of(first, second));

        assertEquals("CITATION", diagnosis.getPrimaryBottleneck());
        assertTrue(diagnosis.getItems().get(0).getReason().contains("2/2"));
    }

    private EvalRun run(Long id) {
        EvalRun run = new EvalRun();
        run.setId(id);
        return run;
    }

    private EvalCaseResult result(Long caseId, Integer hitAt5, Double recallAt5, Double mrr) {
        EvalCaseResult result = new EvalCaseResult();
        result.setCaseId(caseId);
        result.setHitAt5(hitAt5);
        result.setRecallAt5(recallAt5);
        result.setMrr(mrr);
        result.setCitationValid(1);
        result.setMissingCitation(0);
        result.setInvalidCitations("[]");
        result.setLatencyMs(1000L);
        result.setCorrectnessScore(5.0);
        result.setCompletenessScore(5.0);
        result.setFaithfulnessScore(5.0);
        result.setRelevanceScore(5.0);
        result.setCitationQualityScore(5.0);
        return result;
    }
}
