package com.yudong.aistudy.rag.retriever;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.rag.vectorindex.VectorIndexSearchResult;
import com.yudong.aistudy.rag.vectorindex.VectorIndexService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexedVectorRetrieverServiceImpl implements VectorRetrieverService {

    private final VectorIndexService vectorIndexService;

    private final DocumentChunkMapper documentChunkMapper;

    public IndexedVectorRetrieverServiceImpl(VectorIndexService vectorIndexService,
                                             DocumentChunkMapper documentChunkMapper) {
        this.vectorIndexService = vectorIndexService;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public List<RetrievedChunk> retrieveTopK(RetrieverRequest request) {
        if (request == null || request.getVectorTopK() <= 0
                || request.getQueryVector() == null || request.getQueryVector().length == 0) {
            return Collections.emptyList();
        }

        List<VectorIndexSearchResult> searchResults = vectorIndexService.search(
                request.getQueryVector(),
                request.getVectorTopK(),
                getDocumentIds(request)
        );
        if (searchResults.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        List<Long> chunkIds = new ArrayList<>();
        for (VectorIndexSearchResult searchResult : searchResults) {
            if (searchResult.getChunkId() == null || searchResult.getScore() == null) {
                continue;
            }

            scoreMap.put(searchResult.getChunkId(), searchResult.getScore());
            chunkIds.add(searchResult.getChunkId());
        }

        if (chunkIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(DocumentChunk::getId, chunkIds);
        List<Long> documentIds = getDocumentIds(request);
        if (!documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        List<DocumentChunk> chunks = documentChunkMapper.selectList(queryWrapper);
        Map<Long, DocumentChunk> chunkMap = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            chunkMap.put(chunk.getId(), chunk);
        }

        List<RetrievedChunk> retrievedChunks = new ArrayList<>();
        for (Long chunkId : chunkIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            Double similarityScore = scoreMap.get(chunkId);
            if (chunk == null || similarityScore == null) {
                continue;
            }

            RetrievedChunk retrievedChunk = new RetrievedChunk();
            retrievedChunk.setChunk(chunk);
            retrievedChunk.setSimilarityScore(similarityScore);
            retrievedChunk.setRetrieveSource(RetrieveSource.VECTOR);
            retrievedChunk.setVectorRetrieveSource(VectorRetrieveSource.QDRANT);
            retrievedChunks.add(retrievedChunk);
        }

        return retrievedChunks;
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
}
