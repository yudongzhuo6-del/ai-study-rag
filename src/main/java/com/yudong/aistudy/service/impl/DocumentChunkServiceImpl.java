package com.yudong.aistudy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yudong.aistudy.config.properties.ChunkDedupProperties;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.rag.chunk.ChunkDedupService;
import com.yudong.aistudy.rag.embedding.EmbeddingService;
import com.yudong.aistudy.rag.parser.TextParser;
import com.yudong.aistudy.rag.split.SemanticTextSplitterService;
import com.yudong.aistudy.rag.vector.VectorUtils;
import com.yudong.aistudy.rag.vectorindex.VectorIndexService;
import com.yudong.aistudy.service.DocumentChunkService;
import com.yudong.aistudy.service.RedisCacheService;
import com.yudong.aistudy.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Service
public class DocumentChunkServiceImpl implements DocumentChunkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkServiceImpl.class);

    private static final int REUSABLE_CHUNK_QUERY_LIMIT = 20;

    private static final String RETRIEVE_CACHE_PREFIX = "rag:retrieve:kb:";

    private static final String BM25_CACHE_PREFIX = "rag:bm25:kb:";

    @Autowired
    private DocumentChunkMapper chunkMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private SemanticTextSplitterService semanticTextSplitterService;

    @Autowired
    private ChunkDedupService chunkDedupService;

    @Autowired
    private ChunkDedupProperties chunkDedupProperties;

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Override
    @Transactional
    public void processDocument(Long documentId, String objectKey) {
        try {
            deleteChunksByDocumentId(documentId);
            vectorIndexService.deleteByDocumentId(documentId);

            Document currentDocument = documentMapper.selectById(documentId);
            String filename = currentDocument == null ? objectKey : currentDocument.getName();
            String content;
            try (InputStream inputStream = objectStorageService.download(objectKey)) {
                content = TextParser.parse(inputStream, filename);
            }
            List<String> chunks = semanticTextSplitterService.split(content);
            int originalChunkCount = chunks.size();
            chunks = chunkDedupService.dedup(chunks);
            log.info(
                    "Document chunk dedup finished, documentId={}, originalCount={}, dedupedCount={}, removedCount={}",
                    documentId,
                    originalChunkCount,
                    chunks.size(),
                    originalChunkCount - chunks.size()
            );

            int index = 1;
            for (String chunk : chunks) {
                String contentHash = chunkDedupService.calculateHash(chunk);
                DocumentChunk reusableChunk = findReusableChunk(currentDocument, chunk, contentHash);
                String vector = reusableChunk == null ? embeddingService.embed(chunk) : reusableChunk.getVectorId();

                DocumentChunk entity = new DocumentChunk();
                entity.setDocumentId(documentId);
                entity.setContent(chunk);
                entity.setChunkIndex(index++);
                entity.setContentHash(contentHash);
                entity.setVectorId(vector);

                chunkMapper.insert(entity);
                vectorIndexService.upsertChunk(entity, VectorUtils.parseVector(vector));

                if (reusableChunk != null) {
                    log.info(
                            "Document chunk embedding reused, documentId={}, chunkId={}, sourceChunkId={}, contentHash={}",
                            documentId,
                            entity.getId(),
                            reusableChunk.getId(),
                            contentHash
                    );
                }
            }

            updateDocumentStatus(documentId, DocumentStatus.SUCCESS, null);
            clearRetrieveCache(currentDocument, "document processed, documentId=" + documentId);
        } catch (Exception e) {
            deleteChunksByDocumentId(documentId);
            deleteVectorIndexByDocumentIdQuietly(documentId);
            updateDocumentStatus(documentId, DocumentStatus.FAILED, summarizeError(e));
            clearRetrieveCache(documentMapper.selectById(documentId), "document process failed, documentId=" + documentId);
            log.error("Failed to process document chunks, documentId={}, objectKey={}", documentId, objectKey, e);
        }
    }

    private void clearRetrieveCache(Document document, String reason) {//清除redis缓存
        if (document == null || document.getKnowledgeBaseId() == null) {
            log.info("Retrieve cache clear skipped: reason={}, knowledgeBaseId is null", reason);
            return;
        }

        Long knowledgeBaseId = document.getKnowledgeBaseId();
        long deletedCount = redisCacheService.deleteByPrefix(buildRetrieveCachePrefix(knowledgeBaseId));
        long bm25DeletedCount = redisCacheService.deleteByPrefix(buildBm25CachePrefix(knowledgeBaseId));
        log.info(
                "Knowledge base cache cleared: reason={}, knowledgeBaseId={}, retrieveDeletedCount={}, bm25DeletedCount={}",
                reason,
                knowledgeBaseId,
                deletedCount,
                bm25DeletedCount
        );
    }

    private String buildRetrieveCachePrefix(Long knowledgeBaseId) {
        return RETRIEVE_CACHE_PREFIX + knowledgeBaseId + ":";
    }

    private String buildBm25CachePrefix(Long knowledgeBaseId) {
        return BM25_CACHE_PREFIX + knowledgeBaseId + ":";
    }

    private void updateDocumentStatus(Long documentId, Integer status, String processError) {
        if (documentId == null) {
            return;
        }
        LambdaUpdateWrapper<Document> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Document::getId, documentId)
                .set(Document::getStatus, status)
                .set(Document::getProcessError, processError);
        documentMapper.update(null, updateWrapper);
    }

    private String summarizeError(Exception exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }

    private void deleteChunksByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentChunk::getDocumentId, documentId);
        chunkMapper.delete(queryWrapper);
    }

    private void deleteVectorIndexByDocumentIdQuietly(Long documentId) {
        try {
            vectorIndexService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.warn("Failed to clean document vectors from vector index, documentId={}", documentId, e);
        }
    }

    private DocumentChunk findReusableChunk(Document currentDocument, String chunk, String contentHash) {
        if (!chunkDedupProperties.isEnabled()
                || !chunkDedupProperties.isCrossDocumentEnabled()
                || currentDocument == null
                || chunk == null
                || chunk.trim().length() < chunkDedupProperties.getMinLength()
                || contentHash == null
                || contentHash.trim().isEmpty()) {
            return null;
        }

        if (currentDocument == null || currentDocument.getKnowledgeBaseId() == null) {
            return null;
        }

        LambdaQueryWrapper<DocumentChunk> chunkQueryWrapper = new LambdaQueryWrapper<>();
        chunkQueryWrapper.eq(DocumentChunk::getContentHash, contentHash)
                .ne(DocumentChunk::getDocumentId, currentDocument.getId())
                .isNotNull(DocumentChunk::getVectorId)
                .last("LIMIT " + REUSABLE_CHUNK_QUERY_LIMIT);

        List<DocumentChunk> candidates = chunkMapper.selectList(chunkQueryWrapper);
        for (DocumentChunk candidate : candidates) {
            Document sourceDocument = documentMapper.selectById(candidate.getDocumentId());
            if (sourceDocument == null) {
                continue;
            }

            boolean sameKnowledgeBase = currentDocument.getKnowledgeBaseId().equals(sourceDocument.getKnowledgeBaseId());
            boolean sourceReady = sourceDocument.getStatus() != null
                    && sourceDocument.getStatus() == DocumentStatus.SUCCESS;
            if (sameKnowledgeBase && sourceReady) {
                return candidate;
            }
        }

        return null;
    }
}
