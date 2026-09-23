package com.yudong.aistudy.service.impl;

import com.yudong.aistudy.model.dto.eval.EvalMetricResult;
import com.yudong.aistudy.model.dto.eval.GoldenChunkDTO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import com.yudong.aistudy.service.EvalMetricService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EvalMetricServiceImpl implements EvalMetricService {

    @Override
    public EvalMetricResult calculateRetrievalMetrics(List<GoldenChunkDTO> goldenChunks,
                                                       List<ChatSourceVO> sources,
                                                       int topK) {
        EvalMetricResult result = new EvalMetricResult();
        if (goldenChunks == null || goldenChunks.isEmpty() || sources == null || sources.isEmpty() || topK <= 0) {
            result.setRecallAtK(0.0);
            result.setHitAtK(0);
            result.setMrr(0.0);
            return result;
        }

        Set<String> goldenKeys = new HashSet<>();
        for (GoldenChunkDTO goldenChunk : goldenChunks) {
            String key = toKey(goldenChunk);
            if (key != null) {
                goldenKeys.add(key);
            }
        }

        if (goldenKeys.isEmpty()) {
            result.setRecallAtK(0.0);
            result.setHitAtK(0);
            result.setMrr(0.0);
            return result;
        }

        Set<String> matchedKeys = new HashSet<>();
        double reciprocalRank = 0.0;
        int limit = Math.min(topK, sources.size());
        for (int i = 0; i < limit; i++) {
            ChatSourceVO source = sources.get(i);
            String sourceKey = toKey(source);
            if (sourceKey == null || !goldenKeys.contains(sourceKey)) {
                continue;
            }

            matchedKeys.add(sourceKey);
            if (reciprocalRank == 0.0) {
                reciprocalRank = 1.0 / (i + 1);
            }
        }

        result.setRecallAtK((double) matchedKeys.size() / goldenKeys.size());
        result.setHitAtK(matchedKeys.isEmpty() ? 0 : 1);
        result.setMrr(reciprocalRank);
        return result;
    }

    private String toKey(GoldenChunkDTO goldenChunk) {
        if (goldenChunk == null || goldenChunk.getDocumentId() == null || goldenChunk.getChunkIndex() == null) {
            return null;
        }

        return goldenChunk.getDocumentId() + ":" + goldenChunk.getChunkIndex();
    }

    private String toKey(ChatSourceVO source) {
        if (source == null || source.getDocumentId() == null || source.getChunkIndex() == null) {
            return null;
        }

        return source.getDocumentId() + ":" + source.getChunkIndex();
    }
}
