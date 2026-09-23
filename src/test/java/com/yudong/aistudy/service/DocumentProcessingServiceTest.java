package com.yudong.aistudy.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingServiceTest {

    @Test
    void submittedTaskClaimsAndProcessesDocument() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        Document document = document(1L, 7L, DocumentStatus.PENDING);
        when(mapper.selectById(1L)).thenReturn(document);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Executor directExecutor = Runnable::run;
        DocumentProcessingService service = new DocumentProcessingService(directExecutor, mapper, chunkService);

        assertTrue(service.submit(1L));

        verify(chunkService).processDocument(1L, document.getObjectKey());
    }

    @Test
    void rejectedTaskMarksPendingDocumentAsFailed() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        when(mapper.selectById(1L)).thenReturn(document(1L, 7L, DocumentStatus.PENDING));
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Executor rejectingExecutor = command -> { throw new RejectedExecutionException("full"); };
        DocumentProcessingService service = new DocumentProcessingService(rejectingExecutor, mapper, chunkService);

        assertFalse(service.submit(1L));

        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void failedDocumentCanBeRetried() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        Document failed = document(1L, 7L, DocumentStatus.FAILED);
        Document pending = document(1L, 7L, DocumentStatus.PENDING);
        when(mapper.selectById(1L)).thenReturn(failed, pending, pending);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Executor queuedExecutor = command -> { };
        DocumentProcessingService service = new DocumentProcessingService(queuedExecutor, mapper, chunkService);

        assertEquals(DocumentStatus.PENDING, service.retry(1L, 7L).getStatus());
    }

    @Test
    void successfulDocumentCannotBeRetried() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        when(mapper.selectById(1L)).thenReturn(document(1L, 7L, DocumentStatus.SUCCESS));
        DocumentProcessingService service = new DocumentProcessingService(Runnable::run, mapper, chunkService);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.retry(1L, 7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    private Document document(Long id, Long userId, int status) {
        Document document = new Document();
        document.setId(id);
        document.setUserId(userId);
        document.setStatus(status);
        document.setObjectKey("documents/7/1/test.pdf");
        return document;
    }
}
