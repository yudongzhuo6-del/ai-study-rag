package com.yudong.aistudy.rag.retriever;

import com.yudong.aistudy.config.properties.RetrieveProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRetrieverServiceImpl implements HybridRetrieverService {//双路召回的总调度器

    private static final Logger log = LoggerFactory.getLogger(HybridRetrieverServiceImpl.class);

    private final VectorRetrieverService vectorRetrieverService;

    private final KeywordRetrieverService keywordRetrieverService;

    private final RetrieveProperties retrieveProperties;

    public HybridRetrieverServiceImpl(VectorRetrieverService vectorRetrieverService,
                                      KeywordRetrieverService keywordRetrieverService,
                                      RetrieveProperties retrieveProperties) {
        this.vectorRetrieverService = vectorRetrieverService;
        this.keywordRetrieverService = keywordRetrieverService;
        this.retrieveProperties = retrieveProperties;
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrieverRequest request) {
        if (request == null) {
            return new ArrayList<>();
        }

        List<RetrievedChunk> vectorChunks = vectorRetrieverService.retrieveTopK(request);
        List<RetrievedChunk> keywordChunks = keywordRetrieverService.retrieveTopK(request);

        Map<Long, RetrievedChunk> mergedChunks = new LinkedHashMap<>();
        mergeChunks(mergedChunks, vectorChunks);
        mergeChunks(mergedChunks, keywordChunks);

        List<RetrievedChunk> chunks = new ArrayList<>(mergedChunks.values());
        int mergedCount = chunks.size();
        for (RetrievedChunk chunk : chunks) {
            chunk.setHybridRecallScore(calculateHybridRecallScore(chunk));
        }

        chunks.sort((a, b) -> Double.compare(
                b.getHybridRecallScore(),
                a.getHybridRecallScore()
        ));

        int hybridTopK = request.getHybridTopK();
        if (hybridTopK > 0 && chunks.size() > hybridTopK) {
            log.debug(
                    "Hybrid retrieve result: documentIds={}, vectorCount={}, keywordCount={}, mergedCount={}, returnedCount={}, hybridTopK={}",
                    getDocumentIds(request),
                    vectorChunks.size(),
                    keywordChunks.size(),
                    mergedCount,
                    hybridTopK,
                    hybridTopK
            );
            return chunks.subList(0, hybridTopK);
        }

        log.debug(
                "Hybrid retrieve result: documentIds={}, vectorCount={}, keywordCount={}, mergedCount={}, returnedCount={}, hybridTopK={}",
                getDocumentIds(request),
                vectorChunks.size(),
                keywordChunks.size(),
                mergedCount,
                chunks.size(),
                hybridTopK
        );

        return chunks;
    }

    private void mergeChunks(Map<Long, RetrievedChunk> mergedChunks, List<RetrievedChunk> chunks) {
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getChunk() == null || chunk.getChunk().getId() == null) {
                continue;
            }

            Long chunkId = chunk.getChunk().getId();
            RetrievedChunk existingChunk = mergedChunks.get(chunkId);
            if (existingChunk == null) {
                mergedChunks.put(chunkId, chunk);
                continue;
            }

            if (chunk.getSimilarityScore() != null
                    && (existingChunk.getSimilarityScore() == null
                    || chunk.getSimilarityScore() > existingChunk.getSimilarityScore())) {
                existingChunk.setSimilarityScore(chunk.getSimilarityScore());
            }

            if (chunk.getKeywordScore() != null) {
                existingChunk.setKeywordScore(chunk.getKeywordScore());
            }

            if (chunk.getKeywordRecallScore() != null) {
                existingChunk.setKeywordRecallScore(chunk.getKeywordRecallScore());
            }

            if (chunk.getVectorRetrieveSource() != null) {
                existingChunk.setVectorRetrieveSource(chunk.getVectorRetrieveSource());
            }

            existingChunk.setRetrieveSource(mergeRetrieveSource(
                    existingChunk.getRetrieveSource(),
                    chunk.getRetrieveSource()
            ));
        }
    }

    private List<Long> getDocumentIds(RetrieverRequest request) {
        if (request.getFilter() != null) {
            return request.getFilter().effectiveDocumentIds();
        }

        if (request.getDocumentId() != null) {
            return Collections.singletonList(request.getDocumentId());
        }

        return Collections.emptyList();
    }

    private String mergeRetrieveSource(String existingSource, String newSource) {
        if (existingSource == null || existingSource.trim().isEmpty()) {
            return newSource;
        }

        if (newSource == null || newSource.trim().isEmpty()) {
            return existingSource;
        }

        if (existingSource.equals(newSource)) {
            return existingSource;
        }

        return RetrieveSource.HYBRID;
    }

    private double calculateHybridRecallScore(RetrievedChunk chunk) {
        double similarityScore = chunk.getSimilarityScore() == null ? 0.0 : chunk.getSimilarityScore();
        double keywordScore = chunk.getKeywordScore() == null ? 0.0 : chunk.getKeywordScore();
        double normalizedSimilarityScore = normalizeSimilarityScore(similarityScore);
        double normalizedKeywordScore = normalizeKeywordScore(keywordScore);
        double vectorWeight = getVectorWeight();
        double keywordWeight = getKeywordWeight();

        return normalizedSimilarityScore * vectorWeight + normalizedKeywordScore * keywordWeight;
    }

    private double normalizeSimilarityScore(double similarityScore) {
        return Math.max(0.0, Math.min(1.0, similarityScore));
    }

    private double normalizeKeywordScore(double keywordScore) {
        return keywordScore / (1.0 + keywordScore);
    }

    private double getVectorWeight() {
        if (retrieveProperties.getVectorWeight() < 0.0 || retrieveProperties.getKeywordWeight() < 0.0
                || retrieveProperties.getVectorWeight() + retrieveProperties.getKeywordWeight() <= 0.0) {
            return 0.6;
        }

        return retrieveProperties.getVectorWeight();
    }

    private double getKeywordWeight() {
        if (retrieveProperties.getVectorWeight() < 0.0 || retrieveProperties.getKeywordWeight() < 0.0
                || retrieveProperties.getVectorWeight() + retrieveProperties.getKeywordWeight() <= 0.0) {
            return 0.4;
        }

        return retrieveProperties.getKeywordWeight();
    }
}
