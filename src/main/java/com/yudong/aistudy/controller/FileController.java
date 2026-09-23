package com.yudong.aistudy.controller;

import com.yudong.aistudy.common.result.Result;
import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.config.properties.MinioProperties;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.dto.document.DocumentAddDTO;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.vo.document.DocumentUploadResultVO;
import com.yudong.aistudy.service.DocumentService;
import com.yudong.aistudy.service.DocumentProcessingService;
import com.yudong.aistudy.service.DocumentUploadValidationService;
import com.yudong.aistudy.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentUploadValidationService validationService;

    @Autowired
    private MinioProperties minioProperties;

    @PostMapping("/upload")
    public Result<DocumentUploadResultVO> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("userId") Long userId,
                                                  @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        String extension = validationService.validate(file, userId, knowledgeBaseId);
        String fileHash = validationService.calculateSha256(file);
        String originalFilename = file.getOriginalFilename();
        Document existingDocument = documentService.findByFileHash(knowledgeBaseId, fileHash);
        if (existingDocument != null) {
            throw duplicateFileException(existingDocument.getId());
        }
        String objectKey = objectStorageService.upload(file, userId, knowledgeBaseId);

        DocumentAddDTO dto = new DocumentAddDTO();
        dto.setName(originalFilename);
        dto.setStorageType("MINIO");
        dto.setObjectKey(objectKey);
        dto.setBucketName(minioProperties.getBucket());
        dto.setFileSize(file.getSize());
        dto.setContentType(file.getContentType());
        dto.setFileHash(fileHash);
        dto.setType(extension);
        dto.setUserId(userId);
        dto.setKnowledgeBaseId(knowledgeBaseId);

        Long documentId;
        try {
            documentId = documentService.add(dto);
        } catch (DuplicateKeyException e) {
            deleteUploadedObject(objectKey, e);
            Document concurrentDocument = documentService.findByFileHash(knowledgeBaseId, fileHash);
            throw duplicateFileException(concurrentDocument == null ? null : concurrentDocument.getId());
        } catch (RuntimeException e) {
            deleteUploadedObject(objectKey, e);
            throw e;
        }

        boolean submitted = documentProcessingService.submit(documentId);
        DocumentUploadResultVO result = new DocumentUploadResultVO();
        result.setDocumentId(documentId);
        result.setFileName(originalFilename);
        result.setObjectKey(objectKey);
        result.setStatus(submitted ? DocumentStatus.PENDING : DocumentStatus.FAILED);
        return Result.ok(result);
    }

    private BusinessException duplicateFileException(Long documentId) {
        String suffix = documentId == null ? "" : "; existing documentId=" + documentId;
        return new BusinessException(HttpStatus.CONFLICT,
                "The same file already exists in this knowledge base" + suffix);
    }

    private void deleteUploadedObject(String objectKey, RuntimeException originalException) {
        try {
            objectStorageService.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            log.warn("Failed to compensate uploaded object after document insert failure: {}", objectKey,
                    cleanupException);
            originalException.addSuppressed(cleanupException);
        }
    }

    @GetMapping("/{documentId}/download-url")
    public Result<String> downloadUrl(@PathVariable Long documentId,
                                      @RequestParam("userId") Long userId) {
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
        if (!document.isMinioStorage()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Legacy local document does not support a MinIO download URL");
        }
        return Result.ok(objectStorageService.generatePresignedUrl(document.resolveObjectKey()));
    }
}
