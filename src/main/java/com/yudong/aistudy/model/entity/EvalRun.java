package com.yudong.aistudy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_run")
public class EvalRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("run_name")
    private String runName;

    @TableField("config_snapshot")
    private String configSnapshot;

    @TableField("case_count")
    private Integer caseCount;

    @TableField("avg_recall_at_5")
    private Double avgRecallAt5;

    @TableField("hit_rate_at_5")
    private Double hitRateAt5;

    @TableField("avg_mrr")
    private Double avgMrr;

    @TableField("citation_valid_rate")
    private Double citationValidRate;

    @TableField("missing_citation_rate")
    private Double missingCitationRate;

    @TableField("avg_latency_ms")
    private Double avgLatencyMs;

    @TableField("avg_correctness_score")
    private Double avgCorrectnessScore;

    @TableField("avg_completeness_score")
    private Double avgCompletenessScore;

    @TableField("avg_faithfulness_score")
    private Double avgFaithfulnessScore;

    @TableField("avg_relevance_score")
    private Double avgRelevanceScore;

    @TableField("avg_citation_quality_score")
    private Double avgCitationQualityScore;

    @TableField("avg_judge_score")
    private Double avgJudgeScore;

    @TableField("create_time")
    private LocalDateTime createTime;
}
