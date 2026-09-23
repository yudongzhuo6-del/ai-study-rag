package com.yudong.aistudy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_case_result")
public class EvalCaseResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("run_id")
    private Long runId;

    @TableField("case_id")
    private Long caseId;

    private String answer;

    @TableField("retrieved_chunks")
    private String retrievedChunks;

    @TableField("recall_at_5")
    private Double recallAt5;

    @TableField("hit_at_5")
    private Integer hitAt5;

    private Double mrr;

    @TableField("citation_valid")
    private Integer citationValid;

    @TableField("missing_citation")
    private Integer missingCitation;

    @TableField("invalid_citations")
    private String invalidCitations;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("correctness_score")
    private Double correctnessScore;

    @TableField("completeness_score")
    private Double completenessScore;

    @TableField("faithfulness_score")
    private Double faithfulnessScore;

    @TableField("relevance_score")
    private Double relevanceScore;

    @TableField("citation_quality_score")
    private Double citationQualityScore;

    @TableField("judge_score")
    private Double judgeScore;

    @TableField("judge_reason")
    private String judgeReason;

    @TableField("judge_raw_response")
    private String judgeRawResponse;

    @TableField("judge_error_message")
    private String judgeErrorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;
}
