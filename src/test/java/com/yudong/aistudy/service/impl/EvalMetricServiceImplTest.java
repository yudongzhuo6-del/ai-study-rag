package com.yudong.aistudy.service.impl;

import com.yudong.aistudy.model.dto.eval.EvalMetricResult;
import com.yudong.aistudy.model.dto.eval.GoldenChunkDTO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalMetricServiceImplTest {

    private final EvalMetricServiceImpl service = new EvalMetricServiceImpl();

    @Test
    void calculateRetrievalMetricsReturnsHitRecallAndMrrWithinTopK() {
        EvalMetricResult result = service.calculateRetrievalMetrics(
                List.of(golden(10L, 1), golden(20L, 2)),
                List.of(source(99L, 1), source(10L, 1), source(20L, 2)),
                5
        );

        assertEquals(1, result.getHitAtK());
        assertEquals(1.0, result.getRecallAtK());
        assertEquals(0.5, result.getMrr());
    }

    @Test
    void calculateRetrievalMetricsOnlyCountsSourcesInsideTopK() {
        EvalMetricResult result = service.calculateRetrievalMetrics(
                List.of(golden(10L, 1), golden(20L, 2)),
                List.of(source(99L, 1), source(10L, 1), source(88L, 1), source(20L, 2)),
                3
        );

        assertEquals(1, result.getHitAtK());
        assertEquals(0.5, result.getRecallAtK());
        assertEquals(0.5, result.getMrr());
    }

    @Test
    void calculateRetrievalMetricsReturnsZeroWhenNoGoldenChunkMatches() {
        EvalMetricResult result = service.calculateRetrievalMetrics(
                List.of(golden(10L, 1)),
                List.of(source(99L, 1), source(88L, 1)),
                5
        );

        assertEquals(0, result.getHitAtK());
        assertEquals(0.0, result.getRecallAtK());
        assertEquals(0.0, result.getMrr());
    }

    @Test
    void calculateRetrievalMetricsDeduplicatesGoldenChunks() {
        EvalMetricResult result = service.calculateRetrievalMetrics(
                List.of(golden(10L, 1), golden(10L, 1)),
                List.of(source(10L, 1)),
                5
        );

        assertEquals(1, result.getHitAtK());
        assertEquals(1.0, result.getRecallAtK());
        assertEquals(1.0, result.getMrr());
    }

    private GoldenChunkDTO golden(Long documentId, Integer chunkIndex) {
        GoldenChunkDTO goldenChunk = new GoldenChunkDTO();
        goldenChunk.setDocumentId(documentId);
        goldenChunk.setChunkIndex(chunkIndex);
        return goldenChunk;
    }

    private ChatSourceVO source(Long documentId, Integer chunkIndex) {
        ChatSourceVO source = new ChatSourceVO();
        source.setDocumentId(documentId);
        source.setChunkIndex(chunkIndex);
        return source;
    }
}
