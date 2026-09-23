package com.yudong.aistudy.model.vo.eval;

import lombok.Data;

import java.util.List;

@Data
public class EvalRunDiagnosisVO {

    private Long runId;

    private Integer caseCount;

    private String primaryBottleneck;

    private String summary;

    private List<EvalDiagnosisItemVO> items;
}
