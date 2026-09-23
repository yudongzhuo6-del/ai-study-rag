package com.yudong.aistudy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.common.result.Result;
import com.yudong.aistudy.mapper.EvalCaseMapper;
import com.yudong.aistudy.mapper.EvalCaseResultMapper;
import com.yudong.aistudy.mapper.EvalDatasetMapper;
import com.yudong.aistudy.model.dto.eval.EvalCaseCreateDTO;
import com.yudong.aistudy.model.dto.eval.EvalDatasetCreateDTO;
import com.yudong.aistudy.model.dto.eval.EvalRunCreateDTO;
import com.yudong.aistudy.model.entity.EvalCase;
import com.yudong.aistudy.model.entity.EvalCaseResult;
import com.yudong.aistudy.model.entity.EvalDataset;
import com.yudong.aistudy.model.vo.eval.EvalRunDiagnosisVO;
import com.yudong.aistudy.model.vo.eval.EvalRunSummaryVO;
import com.yudong.aistudy.service.EvalDiagnosisService;
import com.yudong.aistudy.service.EvalRunService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/eval")
public class EvalController {

    private final EvalRunService evalRunService;

    private final EvalDiagnosisService evalDiagnosisService;

    private final EvalDatasetMapper evalDatasetMapper;

    private final EvalCaseMapper evalCaseMapper;

    private final EvalCaseResultMapper evalCaseResultMapper;

    private final ObjectMapper objectMapper;

    public EvalController(EvalRunService evalRunService,
                          EvalDiagnosisService evalDiagnosisService,
                          EvalDatasetMapper evalDatasetMapper,
                          EvalCaseMapper evalCaseMapper,
                          EvalCaseResultMapper evalCaseResultMapper,
                          ObjectMapper objectMapper) {
        this.evalRunService = evalRunService;
        this.evalDiagnosisService = evalDiagnosisService;
        this.evalDatasetMapper = evalDatasetMapper;
        this.evalCaseMapper = evalCaseMapper;
        this.evalCaseResultMapper = evalCaseResultMapper;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/datasets")
    public Result<EvalDataset> createDataset(@RequestBody EvalDatasetCreateDTO dto) {
        EvalDataset dataset = new EvalDataset();
        dataset.setName(dto.getName());
        dataset.setDescription(dto.getDescription());
        dataset.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        dataset.setCreateTime(LocalDateTime.now());
        evalDatasetMapper.insert(dataset);
        return Result.ok(dataset);
    }

    @GetMapping("/datasets")
    public Result<List<EvalDataset>> listDatasets() {
        List<EvalDataset> datasets = evalDatasetMapper.selectList(new LambdaQueryWrapper<EvalDataset>()
                .orderByDesc(EvalDataset::getId));
        return Result.ok(datasets);
    }

    @PostMapping("/cases")
    public Result<EvalCase> createCase(@RequestBody EvalCaseCreateDTO dto) {
        EvalCase evalCase = new EvalCase();
        evalCase.setDatasetId(dto.getDatasetId());
        evalCase.setQuestion(dto.getQuestion());
        evalCase.setExpectedAnswer(dto.getExpectedAnswer());
        evalCase.setGoldenChunks(toJson(dto.getGoldenChunks()));
        evalCase.setRequiredKeywords(toJson(dto.getRequiredKeywords()));
        evalCase.setQuestionType(dto.getQuestionType());
        evalCase.setDifficulty(dto.getDifficulty());
        evalCase.setEnabled(1);
        evalCase.setCreateTime(LocalDateTime.now());
        evalCaseMapper.insert(evalCase);
        return Result.ok(evalCase);
    }

    @GetMapping("/datasets/{datasetId}/cases")
    public Result<List<EvalCase>> listCases(@PathVariable Long datasetId) {
        List<EvalCase> cases = evalCaseMapper.selectList(new LambdaQueryWrapper<EvalCase>()
                .eq(EvalCase::getDatasetId, datasetId)
                .orderByAsc(EvalCase::getId));
        return Result.ok(cases);
    }

    @PostMapping("/runs")
    public Result<EvalRunSummaryVO> run(@RequestBody EvalRunCreateDTO dto) {
        return Result.ok(evalRunService.run(dto));
    }

    @GetMapping("/runs/{runId}/results")
    public Result<List<EvalCaseResult>> listResults(@PathVariable Long runId) {
        List<EvalCaseResult> results = evalCaseResultMapper.selectList(new LambdaQueryWrapper<EvalCaseResult>()
                .eq(EvalCaseResult::getRunId, runId)
                .orderByAsc(EvalCaseResult::getId));
        return Result.ok(results);
    }

    @GetMapping("/runs/{runId}/diagnosis")
    public Result<EvalRunDiagnosisVO> diagnose(@PathVariable Long runId) {
        return Result.ok(evalDiagnosisService.diagnose(runId));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
