package com.yudong.aistudy.service;

import com.yudong.aistudy.model.vo.eval.EvalRunDiagnosisVO;

public interface EvalDiagnosisService {

    EvalRunDiagnosisVO diagnose(Long runId);
}
