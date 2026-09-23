package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.eval.EvalRunCreateDTO;
import com.yudong.aistudy.model.vo.eval.EvalRunSummaryVO;

public interface EvalRunService {

    EvalRunSummaryVO run(EvalRunCreateDTO dto);
}
