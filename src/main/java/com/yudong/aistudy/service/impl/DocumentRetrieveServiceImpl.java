package com.yudong.aistudy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yudong.aistudy.config.properties.RetrieveProperties;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.dto.retrieval.RetrieveResult;
import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.rag.embedding.EmbeddingService;
import com.yudong.aistudy.rag.query.QueryRewriteResult;
import com.yudong.aistudy.rag.query.QueryRewriteService;
import com.yudong.aistudy.rag.rerank.RerankService;
import com.yudong.aistudy.rag.retriever.HybridRetrieverService;
import com.yudong.aistudy.rag.retriever.RetrieverRequest;
import com.yudong.aistudy.rag.vector.VectorUtils;
import com.yudong.aistudy.service.DocumentRetrieveService;
import com.yudong.aistudy.service.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HexFormat;

@Service
public class DocumentRetrieveServiceImpl implements DocumentRetrieveService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetrieveServiceImpl.class);

    private static final int DEFAULT_RETRIEVE_DOCUMENT_STATUS = 1;

    private static final String RETRIEVE_CACHE_PREFIX = "rag:retrieve:kb:";

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RerankService rerankService;

    @Autowired
    private HybridRetrieverService hybridRetrieverService;

    @Autowired
    private RetrieveProperties retrieveProperties;

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private RedisCacheService redisCacheService;

    @Value("${rag.retrieve.cache-enabled:true}")
    private boolean retrieveCacheEnabled;

    @Value("${rag.retrieve.cache-ttl-minutes:10}")
    private long retrieveCacheTtlMinutes;

    @Override
    public RetrieveResult retrieve(String question, RetrieveFilter filter) {
        RetrieveResult result = new RetrieveResult();

        if (question == null || question.trim().isEmpty()) {
            result.setChunks(Collections.emptyList());
            result.setVectorDimension(0);
            return result;
        }

        RetrieveFilter effectiveFilter = resolveEffectiveFilter(filter);
        List<Long> effectiveDocumentIds = effectiveFilter.effectiveDocumentIds();
        if (effectiveDocumentIds.isEmpty()) {
            result.setChunks(Collections.emptyList());
            result.setVectorDimension(0);
            log.debug(
                    "RAG retrieve skipped: no documents matched filter, userId={}, knowledgeBaseIds={}, status={}, documentTypes={}",
                    effectiveFilter.getUserId(),
                    effectiveFilter.effectiveKnowledgeBaseIds(),
                    effectiveFilter.getStatus(),
                    effectiveFilter.effectiveDocumentTypes()
            );
            return result;
        }

        String originalQuestion = question.trim();
        String cacheKey = buildRetrieveCacheKey(originalQuestion, effectiveFilter);
        RetrieveResult cachedResult = getCachedRetrieveResult(cacheKey);
        if (cachedResult != null) {
            log.debug(
                    "RAG retrieve cache hit: userId={}, knowledgeBaseIds={}, status={}, documentTypes={}, documentIds={}",
                    effectiveFilter.getUserId(),
                    effectiveFilter.effectiveKnowledgeBaseIds(),
                    effectiveFilter.getStatus(),
                    effectiveFilter.effectiveDocumentTypes(),
                    effectiveDocumentIds
            );
            return cachedResult;
        }

        QueryRewriteResult rewriteResult = queryRewriteService.rewrite(originalQuestion);
        String vectorQuery = fallbackIfBlank(rewriteResult.getVectorQuery(), originalQuestion);
        String keywordQuery = fallbackIfBlank(rewriteResult.getKeywordQuery(), originalQuestion);

        log.info(
                "Query rewrite: original={}, vectorQuery={}, keywordQuery={}, expandedKeywords={}, fallback={}, fallbackReason={}",
                originalQuestion,
                vectorQuery,
                keywordQuery,
                rewriteResult.getExpandedKeywords(),
                rewriteResult.getFallback(),
                rewriteResult.getFallbackReason()
        );

        String questionVectorText = embeddingService.embed(vectorQuery);
        double[] questionVector = VectorUtils.parseVector(questionVectorText);
        result.setVectorDimension(questionVector.length);

        RetrieverRequest retrieverRequest = new RetrieverRequest();
        retrieverRequest.setQuestion(keywordQuery);
        retrieverRequest.setQueryVector(questionVector);
        retrieverRequest.setVectorTopK(retrieveProperties.getRecallTopK());
        retrieverRequest.setKeywordTopK(retrieveProperties.getKeywordTopK());
        retrieverRequest.setHybridTopK(retrieveProperties.getHybridTopK());
        retrieverRequest.setFilter(effectiveFilter);
        if (effectiveDocumentIds.size() == 1) {
            retrieverRequest.setDocumentId(effectiveDocumentIds.get(0));
        }

        log.debug(
                "RAG retrieve request: userId={}, knowledgeBaseIds={}, status={}, documentTypes={}, documentIds={}, vectorTopK={}, keywordTopK={}, hybridTopK={}, finalTopK={}, vectorDimension={}",
                effectiveFilter.getUserId(),
                effectiveFilter.effectiveKnowledgeBaseIds(),
                effectiveFilter.getStatus(),
                effectiveFilter.effectiveDocumentTypes(),
                effectiveDocumentIds,
                retrieverRequest.getVectorTopK(),
                retrieverRequest.getKeywordTopK(),
                retrieverRequest.getHybridTopK(),
                retrieveProperties.getFinalTopK(),
                questionVector.length
        );

        List<RetrievedChunk> recalledChunks = hybridRetrieverService.retrieve(retrieverRequest);

        rerankService.rerank(originalQuestion, recalledChunks);
        List<RetrievedChunk> thresholdChunks = filterByFinalScore(recalledChunks);

        int finalTopK = retrieveProperties.getFinalTopK();
        if (finalTopK <= 0) {
            result.setChunks(Collections.emptyList());
        } else if (thresholdChunks.size() > finalTopK) {
            result.setChunks(thresholdChunks.subList(0, finalTopK));
        } else {
            result.setChunks(thresholdChunks);
        }

        log.debug(
                "RAG retrieve result: userId={}, knowledgeBaseIds={}, status={}, documentTypes={}, documentIds={}, recalledCount={}, thresholdCount={}, returnedCount={}, minFinalScore={}",
                effectiveFilter.getUserId(),
                effectiveFilter.effectiveKnowledgeBaseIds(),
                effectiveFilter.getStatus(),
                effectiveFilter.effectiveDocumentTypes(),
                effectiveDocumentIds,
                recalledChunks.size(),
                thresholdChunks.size(),
                result.getChunks().size(),
                getMinFinalScore()
        );

        cacheRetrieveResult(cacheKey, result);
        return result;
    }

    private RetrieveResult getCachedRetrieveResult(String cacheKey) {
        if (!retrieveCacheEnabled || cacheKey == null) {
            return null;
        }

        return redisCacheService.get(cacheKey, RetrieveResult.class);
    }

    private void cacheRetrieveResult(String cacheKey, RetrieveResult result) {
        if (!retrieveCacheEnabled || cacheKey == null || result == null) {
            return;
        }

        Duration ttl = Duration.ofMinutes(Math.max(1, retrieveCacheTtlMinutes));
        redisCacheService.set(cacheKey, result, ttl);
    }

    private String buildRetrieveCacheKey(String question, RetrieveFilter effectiveFilter) {
        if (!retrieveCacheEnabled || question == null || question.trim().isEmpty() || effectiveFilter == null) {
            return null;
        }

        Long cacheKnowledgeBaseId = getSingleCacheKnowledgeBaseId(effectiveFilter);
        if (cacheKnowledgeBaseId == null) {
            log.debug(
                    "RAG retrieve cache skipped: cache only supports single knowledge base scope, knowledgeBaseIds={}",
                    effectiveFilter.effectiveKnowledgeBaseIds()
            );
            return null;
        }

        String source = "question=" + question.trim()
                + "|userId=" + effectiveFilter.getUserId()
                + "|documentIds=" + sortedLongs(effectiveFilter.effectiveDocumentIds())
                + "|status=" + effectiveFilter.getStatus()
                + "|documentTypes=" + sortedStrings(effectiveFilter.effectiveDocumentTypes())
                + "|recallTopK=" + retrieveProperties.getRecallTopK()
                + "|keywordTopK=" + retrieveProperties.getKeywordTopK()
                + "|hybridTopK=" + retrieveProperties.getHybridTopK()
                + "|finalTopK=" + retrieveProperties.getFinalTopK()
                + "|minFinalScore=" + getMinFinalScore()
                + "|vectorWeight=" + retrieveProperties.getVectorWeight()
                + "|keywordWeight=" + retrieveProperties.getKeywordWeight();

        return buildRetrieveCachePrefix(cacheKnowledgeBaseId) + sha256(source);
    }

    private Long getSingleCacheKnowledgeBaseId(RetrieveFilter effectiveFilter) {
        List<Long> knowledgeBaseIds = sortedLongs(effectiveFilter.effectiveKnowledgeBaseIds());
        if (knowledgeBaseIds.size() != 1) {
            return null;
        }

        return knowledgeBaseIds.get(0);
    }

    private String buildRetrieveCachePrefix(Long knowledgeBaseId) {
        return RETRIEVE_CACHE_PREFIX + knowledgeBaseId + ":";
    }

    private List<Long> sortedLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream()
                .filter(value -> value != null)
                .sorted()
                .toList();
    }

    private List<String> sortedStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim)
                .sorted()
                .toList();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    private String fallbackIfBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value.trim();
    }

    private List<RetrievedChunk> filterByFinalScore(List<RetrievedChunk> chunks) {
        double minFinalScore = getMinFinalScore();
        if (minFinalScore <= 0.0) {
            return chunks;
        }

        List<RetrievedChunk> filteredChunks = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getFinalScore() == null) {
                continue;
            }

            if (chunk.getFinalScore() >= minFinalScore) {
                filteredChunks.add(chunk);
            }
        }

        log.debug(
                "RAG score threshold: minFinalScore={}, before={}, after={}",
                minFinalScore,
                chunks.size(),
                filteredChunks.size()
        );
        return filteredChunks;
    }

    private double getMinFinalScore() {
        if (retrieveProperties.getMinFinalScore() < 0.0) {
            return 0.0;
        }

        return retrieveProperties.getMinFinalScore();
    }

    private RetrieveFilter resolveEffectiveFilter(RetrieveFilter filter) {
        RetrieveFilter sourceFilter = filter == null ? new RetrieveFilter() : filter;
        Integer status = sourceFilter.getStatus() == null ? DEFAULT_RETRIEVE_DOCUMENT_STATUS : sourceFilter.getStatus();
        List<Long> requestedDocumentIds = sourceFilter.effectiveDocumentIds();
        List<Long> requestedKnowledgeBaseIds = sourceFilter.effectiveKnowledgeBaseIds();
        List<String> requestedDocumentTypes = sourceFilter.effectiveDocumentTypes();

        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Document::getId)
                .eq(Document::getStatus, status);
        if (sourceFilter.getUserId() != null) {
            queryWrapper.eq(Document::getUserId, sourceFilter.getUserId());
        }
        if (!requestedKnowledgeBaseIds.isEmpty()) {
            queryWrapper.in(Document::getKnowledgeBaseId, requestedKnowledgeBaseIds);
        }
        if (!requestedDocumentIds.isEmpty()) {
            queryWrapper.in(Document::getId, requestedDocumentIds);
        }
        if (!requestedDocumentTypes.isEmpty()) {
            queryWrapper.in(Document::getType, requestedDocumentTypes);
        }

        List<Document> documents = documentMapper.selectList(queryWrapper);
        RetrieveFilter effectiveFilter = new RetrieveFilter();
        effectiveFilter.setUserId(sourceFilter.getUserId());
        effectiveFilter.setKnowledgeBaseIds(requestedKnowledgeBaseIds);
        effectiveFilter.setStatus(status);
        effectiveFilter.setDocumentTypes(requestedDocumentTypes);
        effectiveFilter.setDocumentIds(documents.stream()
                .map(Document::getId)
                .toList());
        return effectiveFilter;
    }
}
