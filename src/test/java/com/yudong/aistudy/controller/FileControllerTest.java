package com.yudong.aistudy.controller;

import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.config.properties.MinioProperties;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.service.DocumentProcessingService;
import com.yudong.aistudy.service.DocumentService;
import com.yudong.aistudy.service.DocumentUploadValidationService;
import com.yudong.aistudy.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentProcessingService documentProcessingService;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentUploadValidationService validationService;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private MultipartFile file;

    @InjectMocks
    private FileController controller;

    @Test
    void duplicateFileIsRejectedBeforeObjectUpload() {
        when(validationService.validate(file, 7L, 11L)).thenReturn("pdf");
        when(validationService.calculateSha256(file)).thenReturn("same-hash");
        Document existingDocument = new Document();
        existingDocument.setId(42L);
        when(documentService.findByFileHash(11L, "same-hash")).thenReturn(existingDocument);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.upload(file, 7L, 11L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("The same file already exists in this knowledge base; existing documentId=42",
                exception.getMessage());
        verifyNoInteractions(objectStorageService, documentProcessingService);
    }
}
