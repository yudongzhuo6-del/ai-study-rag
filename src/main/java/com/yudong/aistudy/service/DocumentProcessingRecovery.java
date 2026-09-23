package com.yudong.aistudy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yudong.aistudy.config.properties.DocumentProcessingProperties;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentProcessingRecovery {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingRecovery.class);

    private final DocumentMapper documentMapper;
    private final DocumentProcessingService processingService;
    private final DocumentProcessingProperties properties;

    public DocumentProcessingRecovery(DocumentMapper documentMapper,
                                      DocumentProcessingService processingService,
                                      DocumentProcessingProperties properties) {
        this.documentMapper = documentMapper;
        this.processingService = processingService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverUnfinishedDocuments() {
        if (!properties.isRecoveryEnabled()) {
            return;
        }
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<>();
        query.in(Document::getStatus, DocumentStatus.PENDING, DocumentStatus.PROCESSING);
        List<Document> documents = documentMapper.selectList(query);
        log.info("Recovering unfinished document processing tasks, count={}", documents.size());
        documents.forEach(processingService::recover);
    }
}
