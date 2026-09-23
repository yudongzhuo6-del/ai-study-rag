package com.yudong.aistudy.rag.rerank;

import com.yudong.aistudy.config.properties.RerankProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Primary
public class RerankRouterServiceImpl implements RerankService {

    private final ModelRerankServiceImpl modelRerankService;

    private final RerankProperties rerankProperties;

    public RerankRouterServiceImpl(ModelRerankServiceImpl modelRerankService,
                                   RerankProperties rerankProperties) {
        this.modelRerankService = modelRerankService;
        this.rerankProperties = rerankProperties;
    }

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> retrievedChunks) {
        if (!rerankProperties.isEnabled()) {
            return useHybridRecallScore(retrievedChunks);
        }

        try {
            return modelRerankService.rerank(question, retrievedChunks);
        } catch (Exception e) {
            return useHybridRecallScore(retrievedChunks);
        }
    }

    private List<RetrievedChunk> useHybridRecallScore(List<RetrievedChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return Collections.emptyList();
        }

        for (RetrievedChunk retrievedChunk : retrievedChunks) {
            if (retrievedChunk == null) {
                continue;
            }
            double hybridRecallScore = retrievedChunk.getHybridRecallScore() == null
                    ? 0.0
                    : retrievedChunk.getHybridRecallScore();
            retrievedChunk.setFinalScore(hybridRecallScore);
        }

        retrievedChunks.sort((left, right) -> Double.compare(
                scoreOf(right),
                scoreOf(left)
        ));
        return retrievedChunks;
    }

    private double scoreOf(RetrievedChunk chunk) {
        return chunk == null || chunk.getFinalScore() == null ? 0.0 : chunk.getFinalScore();
    }
}
