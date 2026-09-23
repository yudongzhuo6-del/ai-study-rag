package com.yudong.aistudy.model.dto.eval;

import lombok.Data;

@Data
public class EvalJudgeResult {

    private Double correctness;

    private Double completeness;

    private Double faithfulness;

    private Double relevance;

    private Double citationQuality;

    private Double judgeScore;

    private String reason;

    private String rawResponse;
}
