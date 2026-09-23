package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.eval.EvalMetricResult;
import com.yudong.aistudy.model.dto.eval.GoldenChunkDTO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;

import java.util.List;

public interface EvalMetricService {

    EvalMetricResult calculateRetrievalMetrics(List<GoldenChunkDTO> goldenChunks,
                                                List<ChatSourceVO> sources,
                                                int topK);
}
