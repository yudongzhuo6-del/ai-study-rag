package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.eval.EvalJudgeRequest;
import com.yudong.aistudy.model.dto.eval.EvalJudgeResult;

public interface EvalJudgeService {

    EvalJudgeResult judge(EvalJudgeRequest request);
}
