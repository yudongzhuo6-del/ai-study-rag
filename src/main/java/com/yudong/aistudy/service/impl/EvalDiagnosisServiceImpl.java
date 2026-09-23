package com.yudong.aistudy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yudong.aistudy.mapper.EvalCaseResultMapper;
import com.yudong.aistudy.mapper.EvalRunMapper;
import com.yudong.aistudy.model.entity.EvalCaseResult;
import com.yudong.aistudy.model.entity.EvalRun;
import com.yudong.aistudy.model.vo.eval.EvalDiagnosisItemVO;
import com.yudong.aistudy.model.vo.eval.EvalRunDiagnosisVO;
import com.yudong.aistudy.service.EvalDiagnosisService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EvalDiagnosisServiceImpl implements EvalDiagnosisService {

    private static final double LOW_RECALL_THRESHOLD = 0.5;
    private static final double LOW_MRR_THRESHOLD = 0.5;
    private static final double LOW_SCORE_THRESHOLD = 3.0;
    private static final long SLOW_CASE_LATENCY_MS = 5000L;
    private static final double SLOW_RUN_LATENCY_MS = 5000.0;

    private final EvalRunMapper evalRunMapper;

    private final EvalCaseResultMapper evalCaseResultMapper;

    public EvalDiagnosisServiceImpl(EvalRunMapper evalRunMapper, EvalCaseResultMapper evalCaseResultMapper) {
        this.evalRunMapper = evalRunMapper;
        this.evalCaseResultMapper = evalCaseResultMapper;
    }

    @Override
    public EvalRunDiagnosisVO diagnose(Long runId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId is required");
        }

        EvalRun run = evalRunMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("eval run not found: " + runId);
        }

        List<EvalCaseResult> results = evalCaseResultMapper.selectList(new LambdaQueryWrapper<EvalCaseResult>()
                .eq(EvalCaseResult::getRunId, runId)
                .orderByAsc(EvalCaseResult::getId));
        return diagnose(run, results);
    }

    EvalRunDiagnosisVO diagnose(EvalRun run, List<EvalCaseResult> results) {
        List<EvalCaseResult> safeResults = results == null ? new ArrayList<>() : results;
        int caseCount = safeResults.size();

        List<EvalDiagnosisItemVO> items = new ArrayList<>();
        items.add(buildRetrievalRecallItem(run, safeResults, caseCount));
        items.add(buildRetrievalRankingItem(run, safeResults, caseCount));
        items.add(buildContextCompressionItem(safeResults, caseCount));
        items.add(buildGenerationItem(safeResults, caseCount));
        items.add(buildFaithfulnessItem(safeResults, caseCount));
        items.add(buildCitationItem(run, safeResults, caseCount));
        items.add(buildLatencyItem(run, safeResults, caseCount));

        items.sort(Comparator
                .comparing(EvalDiagnosisItemVO::getProblemScore, Comparator.reverseOrder())
                .thenComparing(item -> bottleneckOrder(item.getBottleneck())));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }

        EvalRunDiagnosisVO vo = new EvalRunDiagnosisVO();
        vo.setRunId(run == null ? null : run.getId());
        vo.setCaseCount(caseCount);
        vo.setItems(items);
        if (items.isEmpty()) {
            vo.setPrimaryBottleneck("UNKNOWN");
            vo.setSummary("No evaluation results are available for diagnosis.");
        } else {
            EvalDiagnosisItemVO primary = items.get(0);
            vo.setPrimaryBottleneck(primary.getBottleneck());
            vo.setSummary(buildSummary(primary));
        }
        return vo;
    }

    private EvalDiagnosisItemVO buildRetrievalRecallItem(EvalRun run, List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                intValue(result.getHitAt5()) == 0 || doubleValue(result.getRecallAt5()) < LOW_RECALL_THRESHOLD);
        double hitRate = doubleValue(run == null ? null : run.getHitRateAt5());
        double avgRecall = doubleValue(run == null ? null : run.getAvgRecallAt5());
        double score = clamp(0.6 * (1.0 - hitRate) + 0.4 * (1.0 - avgRecall));
        return item(
                "RETRIEVAL_RECALL",
                score,
                evidence,
                caseCount,
                "%d/%d cases did not hit golden chunks or had low Recall@5.",
                List.of(
                        "Check whether filter.knowledgeBaseIds and document filters are correct.",
                        "Check whether goldenChunks are labeled correctly.",
                        "Increase recallTopK or keywordTopK for comparison.",
                        "Review query rewrite to avoid over-rewriting the question.",
                        "Check chunk splitting and embedding generation quality."
                )
        );
    }

    private EvalDiagnosisItemVO buildRetrievalRankingItem(EvalRun run, List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                intValue(result.getHitAt5()) == 1 && doubleValue(result.getMrr()) < LOW_MRR_THRESHOLD);
        double hitRate = doubleValue(run == null ? null : run.getHitRateAt5());
        double avgMrr = doubleValue(run == null ? null : run.getAvgMrr());
        double score = hitRate >= 0.8 ? clamp(1.0 - avgMrr) : clamp(0.5 * (1.0 - avgMrr));
        return item(
                "RETRIEVAL_RANKING",
                score,
                evidence,
                caseCount,
                "%d/%d cases hit golden chunks but ranked them too low.",
                List.of(
                        "Tune rerank strategy and verify finalScore ordering.",
                        "Adjust vectorWeight and keywordWeight.",
                        "Inspect whether correct chunks are pushed below finalTopK.",
                        "Compare results with rerank disabled and enabled."
                )
        );
    }

    private EvalDiagnosisItemVO buildContextCompressionItem(List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                doubleValue(result.getRecallAt5()) > 0.0
                        && scorePresent(result.getCompletenessScore())
                        && doubleValue(result.getCompletenessScore()) < LOW_SCORE_THRESHOLD
                        && doubleValue(result.getCorrectnessScore()) >= LOW_SCORE_THRESHOLD);
        double score = rate(evidence.size(), caseCount);
        return item(
                "CONTEXT_COMPRESSION",
                score,
                evidence,
                caseCount,
                "%d/%d cases retrieved evidence but missed important answer details.",
                List.of(
                        "Run an A/B test with context compression disabled.",
                        "Increase max-sentences-per-chunk or max-chars-per-chunk.",
                        "Inspect compressedContent in returned sources.",
                        "Increase finalTopK if important evidence is omitted.",
                        "Improve context formatting before sending it to the LLM."
                )
        );
    }

    private EvalDiagnosisItemVO buildGenerationItem(List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                doubleValue(result.getRecallAt5()) >= 0.8
                        && doubleValue(result.getMrr()) >= LOW_MRR_THRESHOLD
                        && scorePresent(result.getCorrectnessScore())
                        && doubleValue(result.getCorrectnessScore()) < LOW_SCORE_THRESHOLD);
        double score = rate(evidence.size(), caseCount);
        return item(
                "GENERATION",
                score,
                evidence,
                caseCount,
                "%d/%d cases retrieved and ranked evidence well but still received low correctness scores.",
                List.of(
                        "Improve the answer prompt.",
                        "Ask the model to extract evidence points before answering.",
                        "Strengthen the rule: answer only from references.",
                        "Compare with a stronger LLM model for generation."
                )
        );
    }

    private EvalDiagnosisItemVO buildFaithfulnessItem(List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                scorePresent(result.getFaithfulnessScore())
                        && doubleValue(result.getFaithfulnessScore()) < LOW_SCORE_THRESHOLD);
        double score = rate(evidence.size(), caseCount);
        return item(
                "FAITHFULNESS",
                score,
                evidence,
                caseCount,
                "%d/%d cases had low faithfulness scores, indicating unsupported claims or hallucination risk.",
                List.of(
                        "Strengthen the prompt to only answer from retrieved sources.",
                        "Require every key claim to include a citation.",
                        "Add a post-generation unsupported-claim check.",
                        "Make the model say it cannot determine the answer when evidence is insufficient."
                )
        );
    }

    private EvalDiagnosisItemVO buildCitationItem(EvalRun run, List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result ->
                intValue(result.getCitationValid()) == 0
                        || intValue(result.getMissingCitation()) == 1
                        || hasInvalidCitations(result.getInvalidCitations())
                        || (scorePresent(result.getCitationQualityScore())
                        && doubleValue(result.getCitationQualityScore()) < LOW_SCORE_THRESHOLD));
        double citationValidRate = doubleValue(run == null ? null : run.getCitationValidRate());
        double missingCitationRate = doubleValue(run == null ? null : run.getMissingCitationRate());
        double score = clamp(0.7 * (1.0 - citationValidRate) + 0.3 * missingCitationRate);
        return item(
                "CITATION",
                score,
                evidence,
                caseCount,
                "%d/%d cases had invalid, missing, or low-quality citations.",
                List.of(
                        "Strengthen prompt rules requiring [n] citations after key claims.",
                        "Keep citation repair enabled and inspect repair failures.",
                        "Reject or repair answers that cite non-existent source indexes.",
                        "Check whether source citation indexes are passed clearly to the LLM."
                )
        );
    }

    private EvalDiagnosisItemVO buildLatencyItem(EvalRun run, List<EvalCaseResult> results, int caseCount) {
        List<Long> evidence = collectCaseIds(results, result -> longValue(result.getLatencyMs()) > SLOW_CASE_LATENCY_MS);
        double avgLatency = doubleValue(run == null ? null : run.getAvgLatencyMs());
        double score = avgLatency > SLOW_RUN_LATENCY_MS ? clamp(avgLatency / 10000.0) : rate(evidence.size(), caseCount);
        return item(
                "LATENCY",
                score,
                evidence,
                caseCount,
                "%d/%d cases were slower than 5000 ms.",
                List.of(
                        "Reduce recallTopK, hybridTopK, or finalTopK and compare quality.",
                        "Check rerank latency and consider disabling it for a test run.",
                        "Run evaluation asynchronously for larger datasets.",
                        "Cache stable embedding or retrieval results during evaluation."
                )
        );
    }

    private EvalDiagnosisItemVO item(String bottleneck,
                                     double problemScore,
                                     List<Long> evidenceCaseIds,
                                     int caseCount,
                                     String reasonTemplate,
                                     List<String> actions) {
        EvalDiagnosisItemVO item = new EvalDiagnosisItemVO();
        item.setBottleneck(bottleneck);
        item.setProblemScore(round(problemScore));
        item.setPriority(priority(problemScore));
        item.setAffectedCaseCount(evidenceCaseIds.size());
        item.setAffectedCaseRate(round(rate(evidenceCaseIds.size(), caseCount)));
        item.setReason(reasonTemplate.formatted(evidenceCaseIds.size(), caseCount));
        item.setEvidenceCaseIds(evidenceCaseIds);
        item.setActions(actions);
        return item;
    }

    private String buildSummary(EvalDiagnosisItemVO primary) {
        if (primary.getProblemScore() <= 0.0) {
            return "No obvious bottleneck was detected from the current evaluation results.";
        }

        return "The top priority is " + primary.getBottleneck()
                + ". " + primary.getReason()
                + " Suggested first action: " + primary.getActions().get(0);
    }

    private List<Long> collectCaseIds(List<EvalCaseResult> results, CasePredicate predicate) {
        List<Long> ids = new ArrayList<>();
        for (EvalCaseResult result : results) {
            if (result != null && predicate.matches(result) && result.getCaseId() != null) {
                ids.add(result.getCaseId());
            }
        }
        return ids;
    }

    private boolean hasInvalidCitations(String invalidCitations) {
        return invalidCitations != null
                && !invalidCitations.trim().isEmpty()
                && !"[]".equals(invalidCitations.trim());
    }

    private boolean scorePresent(Double value) {
        return value != null;
    }

    private double doubleValue(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private long longValue(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double rate(int affectedCount, int caseCount) {
        if (caseCount <= 0) {
            return 0.0;
        }
        return (double) affectedCount / caseCount;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String priority(double score) {
        if (score >= 0.5) {
            return "HIGH";
        }
        if (score >= 0.25) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int bottleneckOrder(String bottleneck) {
        return switch (bottleneck) {
            case "RETRIEVAL_RECALL" -> 1;
            case "RETRIEVAL_RANKING" -> 2;
            case "FAITHFULNESS" -> 3;
            case "GENERATION" -> 4;
            case "CONTEXT_COMPRESSION" -> 5;
            case "CITATION" -> 6;
            case "LATENCY" -> 7;
            default -> 99;
        };
    }

    private interface CasePredicate {
        boolean matches(EvalCaseResult result);
    }
}
