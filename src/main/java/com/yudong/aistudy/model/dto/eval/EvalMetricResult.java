package com.yudong.aistudy.model.dto.eval;

import lombok.Data;

@Data
public class EvalMetricResult {

    private Double recallAtK;

    private Integer hitAtK;

    private Double mrr;
}
