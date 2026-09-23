package com.yudong.aistudy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yudong.aistudy.mapper.DocumentChunkMapper;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.dto.document.DocumentAddDTO;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.model.vo.document.DocumentDeleteResultVO;
import com.yudong.aistudy.rag.vectorindex.VectorIndexService;
import com.yudong.aistudy.service.DocumentService;
import com.yudong.aistudy.service.RedisCacheService;
import com.yudong.aistudy.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private static final String RETRIEVE_CACHE_PREFIX = "rag:retrieve:kb:";

    private static final String BM25_CACHE_PREFIX = "rag:bm25:kb:";

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Override
    public Long add(DocumentAddDTO dto) {
        Document document = new Document();
        BeanUtils.copyProperties(dto, document);
        document.setStatus(DocumentStatus.PENDING);

        documentMapper.insert(document);
        return document.getId();
    }

    @Override
    public Document findByFileHash(Long knowledgeBaseId, String fileHash) {
        if (knowledgeBaseId == null || fileHash == null || fileHash.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getFileHash, fileHash)
                .last("LIMIT 1");
        return documentMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional
    public DocumentDeleteResultVO deleteById(Long documentId) {
        DocumentDeleteResultVO result = new DocumentDeleteResultVO();
        result.setDocumentId(documentId);
        result.setDocumentDeleted(false);
        result.setChunkDeletedCount(0);
        result.setFileDeleted(false);

        if (documentId == null) {
            return result;
        }

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            return result;
        }

        String objectKey = document.resolveObjectKey();
        result.setFilePath(objectKey);
        result.setObjectKey(objectKey);

        LambdaQueryWrapper<DocumentChunk> chunkQueryWrapper = new LambdaQueryWrapper<>();
        chunkQueryWrapper.eq(DocumentChunk::getDocumentId, documentId);
        int chunkDeletedCount = documentChunkMapper.delete(chunkQueryWrapper);
        result.setChunkDeletedCount(chunkDeletedCount);

        deleteVectorIndexByDocumentId(documentId);

        int documentDeletedCount = documentMapper.deleteById(documentId);
        result.setDocumentDeleted(documentDeletedCount > 0);

        result.setFileDeleted(deleteStoredObject(document));
        clearRetrieveCache(document.getKnowledgeBaseId(), "document deleted, documentId=" + documentId);
        return result;
    }

    private void clearRetrieveCache(Long knowledgeBaseId, String reason) {
        if (knowledgeBaseId == null) {
            log.info("Retrieve cache clear skipped: reason={}, knowledgeBaseId is null", reason);
            return;
        }

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

    private boolean deleteStoredObject(Document document) {
        if (document == null || document.resolveObjectKey() == null || document.resolveObjectKey().isBlank()) {
            return false;
        }

        String objectKey = document.resolveObjectKey();
        if (!document.isMinioStorage()) {
            return deleteLegacyLocalFile(objectKey);
        }
        try {
            objectStorageService.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("Failed to delete document object: {}", objectKey, e);
            return false;
        }
        return true;
    }

    private boolean deleteLegacyLocalFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.isRegularFile(path)) {
                return false;
            }
            return Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Failed to delete legacy local document file: {}", filePath, e);
            return false;
        }
    }

    private void deleteVectorIndexByDocumentId(Long documentId) {
        try {
            vectorIndexService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.warn("Failed to delete document vectors from vector index, documentId={}", documentId, e);
        }
    }
}
