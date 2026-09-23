package com.yudong.aistudy.model.vo.eval;

import lombok.Data;

@Data
public class EvalRunSummaryVO {

    private Long runId;

    private Long datasetId;

    private String runName;

    private Integer caseCount;

    private Double avgRecallAt5;

    private Double hitRateAt5;

    private Double avgMrr;

    private Double citationValidRate;

    private Double missingCitationRate;

    private Double avgLatencyMs;

    private Double avgCorrectnessScore;

    private Double avgCompletenessScore;

    private Double avgFaithfulnessScore;

    private Double avgRelevanceScore;

    private Double avgCitationQualityScore;

    private Double avgJudgeScore;
}
