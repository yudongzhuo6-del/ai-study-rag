package com.yudong.aistudy.rag.keyword;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudong.aistudy.config.properties.Bm25Properties;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.service.RedisCacheService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

@Service
public class Bm25StatisticsService {

    private static final int STATISTICS_PAGE_SIZE = 1000;

    private static final String BM25_CACHE_PREFIX = "rag:bm25:kb:";

    private final DocumentChunkMapper documentChunkMapper;

    private final Bm25Properties bm25Properties;

    private final RedisCacheService redisCacheService;

    private final Map<String, CacheEntry> documentFrequencyCache = new HashMap<>();

    private final Map<String, CacheEntry> totalChunkCountCache = new HashMap<>();

    private final Map<String, DoubleCacheEntry> averageDocumentLengthCache = new HashMap<>();

    public Bm25StatisticsService(DocumentChunkMapper documentChunkMapper,
                                 Bm25Properties bm25Properties,
                                 RedisCacheService redisCacheService) {
        this.documentChunkMapper = documentChunkMapper;
        this.bm25Properties = bm25Properties;
        this.redisCacheService = redisCacheService;
    }

    public long getTotalChunkCount(Long documentId) {
        return getTotalChunkCount(toDocumentIds(documentId), null);
    }

    public long getTotalChunkCount(List<Long> documentIds, Long knowledgeBaseId) {
        String redisCacheKey = buildRedisCacheKey(knowledgeBaseId, documentIds, "total", null);
        Long redisValue = getRedisLong(redisCacheKey);
        if (redisValue != null) {
            return redisValue;
        }

        String cacheKey = buildScopeKey(documentIds);
        long now = System.currentTimeMillis();
        CacheEntry cacheEntry = totalChunkCountCache.get(cacheKey);
        if (cacheEntry != null && !isExpired(cacheEntry, now)) {
            return cacheEntry.getValue();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(DocumentChunk::getContent);
        if (documentIds != null && !documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        long totalChunkCount = documentChunkMapper.selectCount(queryWrapper);
        totalChunkCountCache.put(cacheKey, new CacheEntry(totalChunkCount, now));
        setRedisValue(redisCacheKey, totalChunkCount);
        return totalChunkCount;
    }

    public long getDocumentFrequency(String term, Long documentId) {
        return getDocumentFrequency(term, toDocumentIds(documentId), null);
    }

    public long getDocumentFrequency(String term, List<Long> documentIds, Long knowledgeBaseId) {
        if (term == null || term.trim().isEmpty()) {
            return 0;
        }

        String normalizedTerm = term.trim().toLowerCase();
        String redisCacheKey = buildRedisCacheKey(knowledgeBaseId, documentIds, "df", normalizedTerm);
        Long redisValue = getRedisLong(redisCacheKey);
        if (redisValue != null) {
            return redisValue;
        }

        String cacheKey = buildScopeKey(documentIds) + "::" + normalizedTerm;
        long now = System.currentTimeMillis();
        CacheEntry cacheEntry = documentFrequencyCache.get(cacheKey);
        if (cacheEntry != null && !isExpired(cacheEntry, now)) {
            return cacheEntry.getValue();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .isNotNull(DocumentChunk::getContent)
                .like(DocumentChunk::getContent, normalizedTerm);
        if (documentIds != null && !documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        long documentFrequency = documentChunkMapper.selectCount(queryWrapper);
        documentFrequencyCache.put(cacheKey, new CacheEntry(documentFrequency, now));
        setRedisValue(redisCacheKey, documentFrequency);
        return documentFrequency;
    }

    public double getAverageDocumentLength(Long documentId) {
        return getAverageDocumentLength(toDocumentIds(documentId), null);
    }

    public double getAverageDocumentLength(List<Long> documentIds, Long knowledgeBaseId) {
        String redisCacheKey = buildRedisCacheKey(knowledgeBaseId, documentIds, "avg", null);
        Double redisValue = getRedisDouble(redisCacheKey);
        if (redisValue != null) {
            return redisValue;
        }

        String cacheKey = buildScopeKey(documentIds);
        long now = System.currentTimeMillis();
        DoubleCacheEntry cacheEntry = averageDocumentLengthCache.get(cacheKey);
        if (cacheEntry != null && !isExpired(cacheEntry, now)) {
            return cacheEntry.getValue();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(DocumentChunk::getContent);
        if (documentIds != null && !documentIds.isEmpty()) {
            queryWrapper.in(DocumentChunk::getDocumentId, documentIds);
        }

        double totalLength = 0.0;
        long count = 0;
        long pageNo = 1;
        while (true) {
            Page<DocumentChunk> page = new Page<>(pageNo, STATISTICS_PAGE_SIZE, false);
            java.util.List<DocumentChunk> chunks = documentChunkMapper.selectPage(page, queryWrapper).getRecords();
            if (chunks == null || chunks.isEmpty()) {
                break;
            }

            for (DocumentChunk chunk : chunks) {
                if (chunk.getContent() == null) {
                    continue;
                }

                totalLength += chunk.getContent().replaceAll("\\s+", "").length();
                count++;
            }

            if (chunks.size() < STATISTICS_PAGE_SIZE) {
                break;
            }

            pageNo++;
        }

        double averageDocumentLength = count == 0 ? 0.0 : totalLength / count;
        averageDocumentLengthCache.put(cacheKey, new DoubleCacheEntry(averageDocumentLength, now));
        setRedisValue(redisCacheKey, averageDocumentLength);
        return averageDocumentLength;
    }

    private String buildScopeKey(Long documentId) {
        return buildScopeKey(toDocumentIds(documentId));
    }

    private String buildScopeKey(List<Long> documentIds) {
        List<Long> sortedDocumentIds = sortedDocumentIds(documentIds);
        if (sortedDocumentIds.isEmpty()) {
            return "GLOBAL";
        }

        return "DOCS:" + sortedDocumentIds;
    }

    private String buildRedisCacheKey(Long knowledgeBaseId, List<Long> documentIds, String statisticName, String term) {
        if (knowledgeBaseId == null) {
            return null;
        }

        List<Long> sortedDocumentIds = sortedDocumentIds(documentIds);
        if (sortedDocumentIds.isEmpty()) {
            return null;
        }

        String source = "documentIds=" + sortedDocumentIds;
        if (term != null && !term.trim().isEmpty()) {
            source += "|term=" + term.trim().toLowerCase();
        }

        return buildRedisCachePrefix(knowledgeBaseId) + statisticName + ":" + sha256(source);
    }

    public String buildRedisCachePrefix(Long knowledgeBaseId) {
        return BM25_CACHE_PREFIX + knowledgeBaseId + ":";
    }

    private List<Long> toDocumentIds(Long documentId) {
        if (documentId == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(documentId);
    }

    private List<Long> sortedDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }

        return documentIds.stream()
                .filter(id -> id != null)
                .sorted()
                .toList();
    }

    private Long getRedisLong(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }

        return redisCacheService.get(cacheKey, Long.class);
    }

    private Double getRedisDouble(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }

        return redisCacheService.get(cacheKey, Double.class);
    }

    private void setRedisValue(String cacheKey, Object value) {
        if (cacheKey == null || value == null) {
            return;
        }

        redisCacheService.set(cacheKey, value, Duration.ofMillis(getCacheTtlMillis()));
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

    private boolean isExpired(CacheEntry cacheEntry, long now) {
        return now - cacheEntry.getCreatedAt() > getCacheTtlMillis();
    }

    private boolean isExpired(DoubleCacheEntry cacheEntry, long now) {
        return now - cacheEntry.getCreatedAt() > getCacheTtlMillis();
    }

    private long getCacheTtlMillis() {
        if (bm25Properties.getStatisticsCacheTtlMillis() <= 0) {
            return 300_000L;
        }

        return bm25Properties.getStatisticsCacheTtlMillis();
    }

    private static class CacheEntry {

        private final long value;

        private final long createdAt;

        private CacheEntry(long value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }

        private long getValue() {
            return value;
        }

        private long getCreatedAt() {
            return createdAt;
        }
    }

    private static class DoubleCacheEntry {

        private final double value;

        private final long createdAt;

        private DoubleCacheEntry(double value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }

        private double getValue() {
            return value;
        }

        private long getCreatedAt() {
            return createdAt;
        }
    }
}
