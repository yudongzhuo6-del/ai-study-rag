package com.yudong.aistudy.rag.retriever;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudong.aistudy.config.properties.RetrieveProperties;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.rag.vector.VectorUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Service
public class InMemoryVectorRetrieverServiceImpl implements VectorRetrieverService {

    private final DocumentChunkMapper documentChunkMapper;

    private final RetrieveProperties retrieveProperties;

    public InMemoryVectorRetrieverServiceImpl(DocumentChunkMapper documentChunkMapper,
                                              RetrieveProperties retrieveProperties) {
        this.documentChunkMapper = documentChunkMapper;
        this.retrieveProperties = retrieveProperties;
    }

    @Override
    public List<RetrievedChunk> retrieveTopK(RetrieverRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        double[] queryVector = request.getQueryVector();
        int topK = request.getVectorTopK();
        if (topK <= 0 || queryVector == null || queryVector.length == 0) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(DocumentChunk::getVectorId);
        List<Long> documentIds = getDocumentIds(request);
        if (!documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        PriorityQueue<RetrievedChunk> topKQueue = new PriorityQueue<>(
                Comparator.comparing(RetrievedChunk::getSimilarityScore)
        );

        long pageNo = 1;
        int pageSize = getPageSize();
        while (true) {
            Page<DocumentChunk> page = new Page<>(pageNo, pageSize, false);
            List<DocumentChunk> chunks = documentChunkMapper.selectPage(page, queryWrapper).getRecords();
            if (chunks == null || chunks.isEmpty()) {
                break;
            }

            for (DocumentChunk chunk : chunks) {
                RetrievedChunk retrievedChunk = buildRetrievedChunk(queryVector, chunk);
                if (retrievedChunk == null) {
                    continue;
                }

                topKQueue.offer(retrievedChunk);
                if (topKQueue.size() > topK) {
                    topKQueue.poll();
                }
            }

            if (chunks.size() < pageSize) {
                break;
            }

            pageNo++;
        }

        List<RetrievedChunk> retrievedChunks = new ArrayList<>(topKQueue);
        retrievedChunks.sort((a, b) -> Double.compare(
                b.getSimilarityScore(),
                a.getSimilarityScore()
        ));
        return retrievedChunks;
    }

    private int getPageSize() {
        if (retrieveProperties.getPageSize() <= 0) {
            return 1000;
        }

        return retrieveProperties.getPageSize();
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

    private RetrievedChunk buildRetrievedChunk(double[] queryVector, DocumentChunk chunk) {
        try {
            double[] chunkVector = VectorUtils.parseVector(chunk.getVectorId());
            if (chunkVector.length == 0 || chunkVector.length != queryVector.length) {
                return null;
            }

            double similarityScore = VectorUtils.cosineSimilarity(queryVector, chunkVector);

            RetrievedChunk retrievedChunk = new RetrievedChunk();
            retrievedChunk.setChunk(chunk);
            retrievedChunk.setSimilarityScore(similarityScore);
            retrievedChunk.setRetrieveSource(RetrieveSource.VECTOR);
            retrievedChunk.setVectorRetrieveSource(VectorRetrieveSource.IN_MEMORY);
            return retrievedChunk;
        } catch (Exception e) {
            return null;
        }
    }
}
