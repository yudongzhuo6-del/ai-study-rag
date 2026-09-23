package com.yudong.aistudy.model.vo.eval;

import lombok.Data;

import java.util.List;

@Data
public class EvalDiagnosisItemVO {

    private Integer rank;

    private String bottleneck;

    private String priority;

    private Double problemScore;

    private Integer affectedCaseCount;

    private Double affectedCaseRate;

    private String reason;

    private List<Long> evidenceCaseIds;

    private List<String> actions;
}
