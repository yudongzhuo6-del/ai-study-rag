package com.yudong.aistudy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.mapper.EvalCaseMapper;
import com.yudong.aistudy.mapper.EvalCaseResultMapper;
import com.yudong.aistudy.mapper.EvalRunMapper;
import com.yudong.aistudy.model.dto.eval.EvalJudgeRequest;
import com.yudong.aistudy.model.dto.eval.EvalJudgeResult;
import com.yudong.aistudy.model.dto.eval.EvalMetricResult;
import com.yudong.aistudy.model.dto.eval.EvalRunCreateDTO;
import com.yudong.aistudy.model.dto.eval.GoldenChunkDTO;
import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import com.yudong.aistudy.model.entity.EvalCase;
import com.yudong.aistudy.model.entity.EvalCaseResult;
import com.yudong.aistudy.model.entity.EvalRun;
import com.yudong.aistudy.model.vo.chat.ChatAnswerVO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import com.yudong.aistudy.model.vo.eval.EvalRunSummaryVO;
import com.yudong.aistudy.rag.citation.CitationValidationResult;
import com.yudong.aistudy.rag.citation.CitationValidator;
import com.yudong.aistudy.service.ChatService;
import com.yudong.aistudy.service.EvalJudgeService;
import com.yudong.aistudy.service.EvalMetricService;
import com.yudong.aistudy.service.EvalRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class EvalRunServiceImpl implements EvalRunService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunServiceImpl.class);

    private static final int DEFAULT_TOP_K = 5;

    private final EvalCaseMapper evalCaseMapper;

    private final EvalRunMapper evalRunMapper;

    private final EvalCaseResultMapper evalCaseResultMapper;

    private final ChatService chatService;

    private final CitationValidator citationValidator;

    private final EvalMetricService evalMetricService;

    private final EvalJudgeService evalJudgeService;

    private final ObjectMapper objectMapper;

    public EvalRunServiceImpl(EvalCaseMapper evalCaseMapper,
                              EvalRunMapper evalRunMapper,
                              EvalCaseResultMapper evalCaseResultMapper,
                              ChatService chatService,
                              CitationValidator citationValidator,
                              EvalMetricService evalMetricService,
                              EvalJudgeService evalJudgeService,
                              ObjectMapper objectMapper) {
        this.evalCaseMapper = evalCaseMapper;
        this.evalRunMapper = evalRunMapper;
        this.evalCaseResultMapper = evalCaseResultMapper;
        this.chatService = chatService;
        this.citationValidator = citationValidator;
        this.evalMetricService = evalMetricService;
        this.evalJudgeService = evalJudgeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvalRunSummaryVO run(EvalRunCreateDTO dto) {
        if (dto == null || dto.getDatasetId() == null) {
            throw new IllegalArgumentException("datasetId is required");
        }

        List<EvalCase> cases = evalCaseMapper.selectList(new LambdaQueryWrapper<EvalCase>()
                .eq(EvalCase::getDatasetId, dto.getDatasetId())
                .eq(EvalCase::getEnabled, 1)
                .orderByAsc(EvalCase::getId));

        EvalRun run = new EvalRun();
        run.setDatasetId(dto.getDatasetId());
        run.setRunName(dto.getRunName());
        run.setConfigSnapshot(toJson(dto));
        run.setCaseCount(cases.size());
        run.setCreateTime(LocalDateTime.now());
        evalRunMapper.insert(run);

        double recallSum = 0.0;
        double hitSum = 0.0;
        double mrrSum = 0.0;
        double citationValidSum = 0.0;
        double missingCitationSum = 0.0;
        double latencySum = 0.0;
        double correctnessSum = 0.0;
        double completenessSum = 0.0;
        double faithfulnessSum = 0.0;
        double relevanceSum = 0.0;
        double citationQualitySum = 0.0;
        double judgeScoreSum = 0.0;
        int judgeCount = 0;

        for (EvalCase evalCase : cases) {
            EvalCaseResult caseResult = executeCase(run.getId(), evalCase, dto.getFilter(), DEFAULT_TOP_K);
            evalCaseResultMapper.insert(caseResult);

            recallSum += valueOrZero(caseResult.getRecallAt5());
            hitSum += valueOrZero(caseResult.getHitAt5());
            mrrSum += valueOrZero(caseResult.getMrr());
            citationValidSum += valueOrZero(caseResult.getCitationValid());
            missingCitationSum += valueOrZero(caseResult.getMissingCitation());
            latencySum += valueOrZero(caseResult.getLatencyMs());
            if (caseResult.getJudgeScore() != null) {
                correctnessSum += valueOrZero(caseResult.getCorrectnessScore());
                completenessSum += valueOrZero(caseResult.getCompletenessScore());
                faithfulnessSum += valueOrZero(caseResult.getFaithfulnessScore());
                relevanceSum += valueOrZero(caseResult.getRelevanceScore());
                citationQualitySum += valueOrZero(caseResult.getCitationQualityScore());
                judgeScoreSum += valueOrZero(caseResult.getJudgeScore());
                judgeCount++;
            }
        }

        applySummary(run, cases.size(), recallSum, hitSum, mrrSum, citationValidSum, missingCitationSum, latencySum);
        applyJudgeSummary(run, judgeCount, correctnessSum, completenessSum, faithfulnessSum, relevanceSum,
                citationQualitySum, judgeScoreSum);
        evalRunMapper.updateById(run);
        return toSummaryVO(run);
    }

    private EvalCaseResult executeCase(Long runId, EvalCase evalCase, RetrieveFilter filter, int topK) {
        EvalCaseResult result = new EvalCaseResult();
        result.setRunId(runId);
        result.setCaseId(evalCase.getId());
        result.setCreateTime(LocalDateTime.now());

        long start = System.currentTimeMillis();
        try {
            ChatAnswerVO answerVO = chatService.ask(evalCase.getQuestion(), filter);
            long latencyMs = System.currentTimeMillis() - start;
            List<ChatSourceVO> sources = answerVO.getSources() == null ? Collections.emptyList() : answerVO.getSources();
            List<GoldenChunkDTO> goldenChunks = parseGoldenChunks(evalCase.getGoldenChunks());
            EvalMetricResult metricResult = evalMetricService.calculateRetrievalMetrics(goldenChunks, sources, topK);
            CitationValidationResult citationResult = citationValidator.validate(answerVO.getAnswer(), sources);

            result.setAnswer(answerVO.getAnswer());
            result.setRetrievedChunks(toJson(sources));
            result.setRecallAt5(metricResult.getRecallAtK());
            result.setHitAt5(metricResult.getHitAtK());
            result.setMrr(metricResult.getMrr());
            result.setCitationValid(citationResult.isValid() ? 1 : 0);
            result.setMissingCitation(citationResult.isMissingCitation() ? 1 : 0);
            result.setInvalidCitations(toJson(citationResult.getInvalidIndexes()));
            result.setLatencyMs(latencyMs);
            applyJudgeResult(result, evalCase, answerVO, sources, citationResult);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Eval case failed: runId={}, caseId={}", runId, evalCase.getId(), e);
            result.setRecallAt5(0.0);
            result.setHitAt5(0);
            result.setMrr(0.0);
            result.setCitationValid(0);
            result.setMissingCitation(0);
            result.setLatencyMs(latencyMs);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    private void applyJudgeResult(EvalCaseResult result,
                                  EvalCase evalCase,
                                  ChatAnswerVO answerVO,
                                  List<ChatSourceVO> sources,
                                  CitationValidationResult citationResult) {
        try {
            EvalJudgeRequest request = new EvalJudgeRequest();
            request.setQuestion(evalCase.getQuestion());
            request.setExpectedAnswer(evalCase.getExpectedAnswer());
            request.setRequiredKeywords(evalCase.getRequiredKeywords());
            request.setSources(sources);
            request.setActualAnswer(answerVO.getAnswer());
            request.setCitationValid(citationResult.isValid());
            request.setMissingCitation(citationResult.isMissingCitation());
            request.setInvalidCitations(citationResult.getInvalidIndexes());

            EvalJudgeResult judgeResult = evalJudgeService.judge(request);
            if (judgeResult == null) {
                return;
            }

            result.setCorrectnessScore(judgeResult.getCorrectness());
            result.setCompletenessScore(judgeResult.getCompleteness());
            result.setFaithfulnessScore(judgeResult.getFaithfulness());
            result.setRelevanceScore(judgeResult.getRelevance());
            result.setCitationQualityScore(judgeResult.getCitationQuality());
            result.setJudgeScore(judgeResult.getJudgeScore());
            result.setJudgeReason(judgeResult.getReason());
            result.setJudgeRawResponse(judgeResult.getRawResponse());
        } catch (Exception e) {
            log.warn("Eval judge failed: runId={}, caseId={}", result.getRunId(), evalCase.getId(), e);
            result.setJudgeErrorMessage(e.getMessage());
        }
    }

    private void applySummary(EvalRun run,
                              int caseCount,
                              double recallSum,
                              double hitSum,
                              double mrrSum,
                              double citationValidSum,
                              double missingCitationSum,
                              double latencySum) {
        if (caseCount <= 0) {
            run.setAvgRecallAt5(0.0);
            run.setHitRateAt5(0.0);
            run.setAvgMrr(0.0);
            run.setCitationValidRate(0.0);
            run.setMissingCitationRate(0.0);
            run.setAvgLatencyMs(0.0);
            return;
        }

        run.setAvgRecallAt5(recallSum / caseCount);
        run.setHitRateAt5(hitSum / caseCount);
        run.setAvgMrr(mrrSum / caseCount);
        run.setCitationValidRate(citationValidSum / caseCount);
        run.setMissingCitationRate(missingCitationSum / caseCount);
        run.setAvgLatencyMs(latencySum / caseCount);
    }

    private void applyJudgeSummary(EvalRun run,
                                   int judgeCount,
                                   double correctnessSum,
                                   double completenessSum,
                                   double faithfulnessSum,
                                   double relevanceSum,
                                   double citationQualitySum,
                                   double judgeScoreSum) {
        if (judgeCount <= 0) {
            run.setAvgCorrectnessScore(0.0);
            run.setAvgCompletenessScore(0.0);
            run.setAvgFaithfulnessScore(0.0);
            run.setAvgRelevanceScore(0.0);
            run.setAvgCitationQualityScore(0.0);
            run.setAvgJudgeScore(0.0);
            return;
        }

        run.setAvgCorrectnessScore(correctnessSum / judgeCount);
        run.setAvgCompletenessScore(completenessSum / judgeCount);
        run.setAvgFaithfulnessScore(faithfulnessSum / judgeCount);
        run.setAvgRelevanceScore(relevanceSum / judgeCount);
        run.setAvgCitationQualityScore(citationQualitySum / judgeCount);
        run.setAvgJudgeScore(judgeScoreSum / judgeCount);
    }

    private List<GoldenChunkDTO> parseGoldenChunks(String goldenChunks) {
        if (goldenChunks == null || goldenChunks.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(goldenChunks, new TypeReference<List<GoldenChunkDTO>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Parse goldenChunks failed: {}", goldenChunks, e);
            return Collections.emptyList();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private double valueOrZero(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private EvalRunSummaryVO toSummaryVO(EvalRun run) {
        EvalRunSummaryVO vo = new EvalRunSummaryVO();
        vo.setRunId(run.getId());
        vo.setDatasetId(run.getDatasetId());
        vo.setRunName(run.getRunName());
        vo.setCaseCount(run.getCaseCount());
        vo.setAvgRecallAt5(run.getAvgRecallAt5());
        vo.setHitRateAt5(run.getHitRateAt5());
        vo.setAvgMrr(run.getAvgMrr());
        vo.setCitationValidRate(run.getCitationValidRate());
        vo.setMissingCitationRate(run.getMissingCitationRate());
        vo.setAvgLatencyMs(run.getAvgLatencyMs());
        vo.setAvgCorrectnessScore(run.getAvgCorrectnessScore());
        vo.setAvgCompletenessScore(run.getAvgCompletenessScore());
        vo.setAvgFaithfulnessScore(run.getAvgFaithfulnessScore());
        vo.setAvgRelevanceScore(run.getAvgRelevanceScore());
        vo.setAvgCitationQualityScore(run.getAvgCitationQualityScore());
        vo.setAvgJudgeScore(run.getAvgJudgeScore());
        return vo;
    }
}
