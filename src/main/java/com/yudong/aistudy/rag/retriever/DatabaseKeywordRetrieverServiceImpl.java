package com.yudong.aistudy.rag.retriever;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudong.aistudy.config.properties.RetrieveProperties;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.rag.keyword.Bm25Scorer;
import com.yudong.aistudy.rag.keyword.KeywordScorer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Service
public class DatabaseKeywordRetrieverServiceImpl implements KeywordRetrieverService {

    private final DocumentChunkMapper documentChunkMapper;

    private final RetrieveProperties retrieveProperties;

    private final KeywordScorer keywordScorer;

    private final Bm25Scorer bm25Scorer;

    public DatabaseKeywordRetrieverServiceImpl(DocumentChunkMapper documentChunkMapper,
                                               RetrieveProperties retrieveProperties,
                                               KeywordScorer keywordScorer,
                                               Bm25Scorer bm25Scorer) {
        this.documentChunkMapper = documentChunkMapper;
        this.retrieveProperties = retrieveProperties;
        this.keywordScorer = keywordScorer;
        this.bm25Scorer = bm25Scorer;
    }

    @Override
    public List<RetrievedChunk> retrieveTopK(RetrieverRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        String question = request.getQuestion();
        int topK = request.getKeywordTopK();
        if (topK <= 0 || question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = keywordScorer.splitKeywordsForSearch(question);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .isNotNull(DocumentChunk::getContent)
                .and(wrapper -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        if (i == 0) {
                            wrapper.like(DocumentChunk::getContent, keywords.get(i));
                        } else {
                            wrapper.or().like(DocumentChunk::getContent, keywords.get(i));
                        }
                    }
                });
        List<Long> documentIds = getDocumentIds(request);
        if (!documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        PriorityQueue<RetrievedChunk> topKQueue = new PriorityQueue<>(
                Comparator.comparing(RetrievedChunk::getKeywordScore)
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
                RetrievedChunk retrievedChunk = buildRetrievedChunk(
                        keywords,
                        question,
                        chunk,
                        documentIds,
                        getSingleKnowledgeBaseId(request)
                );
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
                b.getKeywordScore(),
                a.getKeywordScore()
        ));

        return retrievedChunks;
    }

    private RetrievedChunk buildRetrievedChunk(List<String> keywords,
                                               String question,
                                               DocumentChunk chunk,
                                               List<Long> documentIds,
                                               Long knowledgeBaseId) {
        double bm25Score = bm25Scorer.score(keywords, chunk.getContent(), question, documentIds, knowledgeBaseId);
        if (bm25Score <= 0.0) {
            return null;
        }

        RetrievedChunk retrievedChunk = new RetrievedChunk();
        retrievedChunk.setChunk(chunk);
        retrievedChunk.setSimilarityScore(0.0);
        retrievedChunk.setKeywordScore(bm25Score);
        retrievedChunk.setKeywordRecallScore(bm25Score);
        retrievedChunk.setRetrieveSource(RetrieveSource.KEYWORD);
        return retrievedChunk;
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

    private Long getSingleKnowledgeBaseId(RetrieverRequest request) {
        if (request == null || request.getFilter() == null) {
            return null;
        }

        List<Long> knowledgeBaseIds = request.getFilter().effectiveKnowledgeBaseIds();
        if (knowledgeBaseIds.size() != 1) {
            return null;
        }

        return knowledgeBaseIds.get(0);
    }

}
