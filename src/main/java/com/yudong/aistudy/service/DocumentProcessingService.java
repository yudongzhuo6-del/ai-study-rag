package com.yudong.aistudy.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.vo.document.DocumentProcessStatusVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final String QUEUE_FULL_ERROR = "Document processing queue is full; retry later";

    private final Executor documentTaskExecutor;
    private final DocumentMapper documentMapper;
    private final DocumentChunkService documentChunkService;

    public DocumentProcessingService(@Qualifier("documentTaskExecutor") Executor documentTaskExecutor,
                                     DocumentMapper documentMapper,
                                     DocumentChunkService documentChunkService) {
        this.documentTaskExecutor = documentTaskExecutor;
        this.documentMapper = documentMapper;
        this.documentChunkService = documentChunkService;
    }

    public boolean submit(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || document.getStatus() == null || document.getStatus() != DocumentStatus.PENDING) {
            return false;
        }
        String objectKey = document.resolveObjectKey();
        try {
            documentTaskExecutor.execute(() -> processPendingDocument(documentId, objectKey));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            markPendingAsFailed(documentId, QUEUE_FULL_ERROR);
            log.warn("Document processing task rejected, documentId={}", documentId, e);
            return false;
        }
    }

    public DocumentProcessStatusVO getStatus(Long documentId, Long userId) {
        return toStatusVO(requireOwnedDocument(documentId, userId));
    }

    public DocumentProcessStatusVO retry(Long documentId, Long userId) {
        Document document = requireOwnedDocument(documentId, userId);
        if (document.getStatus() == null || document.getStatus() != DocumentStatus.FAILED) {
            throw new BusinessException(HttpStatus.CONFLICT, "Only failed documents can be retried");
        }

        LambdaUpdateWrapper<Document> retryUpdate = new LambdaUpdateWrapper<>();
        retryUpdate.eq(Document::getId, documentId)
                .eq(Document::getStatus, DocumentStatus.FAILED)
                .set(Document::getStatus, DocumentStatus.PENDING)
                .set(Document::getProcessError, null);
        if (documentMapper.update(null, retryUpdate) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "Document status changed; refresh and retry");
        }
        submit(documentId);
        return toStatusVO(documentMapper.selectById(documentId));
    }

    public void recover(Document document) {
        if (document == null || document.getId() == null || document.getStatus() == null) {
            return;
        }
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            LambdaUpdateWrapper<Document> reset = new LambdaUpdateWrapper<>();
            reset.eq(Document::getId, document.getId())
                    .eq(Document::getStatus, DocumentStatus.PROCESSING)
                    .set(Document::getStatus, DocumentStatus.PENDING)
                    .set(Document::getProcessError, "Recovered after application restart");
            documentMapper.update(null, reset);
        }
        submit(document.getId());
    }

    private void processPendingDocument(Long documentId, String objectKey) {
        LambdaUpdateWrapper<Document> claim = new LambdaUpdateWrapper<>();
        claim.eq(Document::getId, documentId)
                .eq(Document::getStatus, DocumentStatus.PENDING)
                .set(Document::getStatus, DocumentStatus.PROCESSING)
                .set(Document::getProcessError, null);
        if (documentMapper.update(null, claim) != 1) {
            log.info("Document processing skipped because task was already claimed, documentId={}", documentId);
            return;
        }
        documentChunkService.processDocument(documentId, objectKey);
    }

    private void markPendingAsFailed(Long documentId, String error) {
        LambdaUpdateWrapper<Document> update = new LambdaUpdateWrapper<>();
        update.eq(Document::getId, documentId)
                .eq(Document::getStatus, DocumentStatus.PENDING)
                .set(Document::getStatus, DocumentStatus.FAILED)
                .set(Document::getProcessError, error);
        documentMapper.update(null, update);
    }

    private Document requireOwnedDocument(Long documentId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userId is required and must be positive");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Document not found");
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Document does not belong to this user");
        }
        return document;
    }

    private DocumentProcessStatusVO toStatusVO(Document document) {
        DocumentProcessStatusVO result = new DocumentProcessStatusVO();
        result.setDocumentId(document.getId());
        result.setStatus(document.getStatus());
        result.setStatusName(statusName(document.getStatus()));
        result.setProcessError(document.getProcessError());
        result.setUpdateTime(document.getUpdateTime());
        return result;
    }

    private String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case DocumentStatus.PENDING -> "PENDING";
            case DocumentStatus.SUCCESS -> "SUCCESS";
            case DocumentStatus.FAILED -> "FAILED";
            case DocumentStatus.PROCESSING -> "PROCESSING";
            default -> "UNKNOWN";
        };
    }
}
