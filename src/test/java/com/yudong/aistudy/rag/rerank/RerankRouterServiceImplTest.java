package com.yudong.aistudy.rag.rerank;

import com.yudong.aistudy.config.properties.RerankProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RerankRouterServiceImplTest {

    @Test
    void disabledModelUsesHybridScoreWithoutOverwritingBm25Score() {
        ModelRerankServiceImpl modelService = mock(ModelRerankServiceImpl.class);
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(false);
        RerankRouterServiceImpl service = new RerankRouterServiceImpl(modelService, properties);
        RetrievedChunk lower = chunk(0.35, 2.4);
        RetrievedChunk higher = chunk(0.80, 4.2);
        List<RetrievedChunk> chunks = new ArrayList<>(List.of(lower, higher));

        List<RetrievedChunk> result = service.rerank("question", chunks);

        assertSame(higher, result.get(0));
        assertEquals(0.80, higher.getFinalScore());
        assertEquals(4.2, higher.getKeywordScore());
        assertEquals(0.35, lower.getFinalScore());
        verifyNoInteractions(modelService);
    }

    @Test
    void modelFailureFallsBackToHybridScore() {
        ModelRerankServiceImpl modelService = mock(ModelRerankServiceImpl.class);
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(true);
        when(modelService.rerank(anyString(), anyList())).thenThrow(new IllegalStateException("unavailable"));
        RerankRouterServiceImpl service = new RerankRouterServiceImpl(modelService, properties);
        RetrievedChunk chunk = chunk(0.65, 3.0);

        List<RetrievedChunk> result = service.rerank("question", new ArrayList<>(List.of(chunk)));

        assertEquals(0.65, result.get(0).getFinalScore());
        assertEquals(3.0, result.get(0).getKeywordScore());
    }

    private RetrievedChunk chunk(double hybridScore, double keywordScore) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setHybridRecallScore(hybridScore);
        chunk.setKeywordScore(keywordScore);
        return chunk;
    }
}
