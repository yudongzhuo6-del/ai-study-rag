package com.yudong.aistudy.service;

import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.config.properties.DocumentUploadProperties;
import com.yudong.aistudy.mapper.KnowledgeBaseMapper;
import com.yudong.aistudy.model.entity.KnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentUploadValidationServiceTest {

    private KnowledgeBaseMapper knowledgeBaseMapper;
    private DocumentUploadValidationService service;

    @BeforeEach
    void setUp() {
        DocumentUploadProperties properties = new DocumentUploadProperties();
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        service = new DocumentUploadValidationService(properties, knowledgeBaseMapper);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(10L);
        knowledgeBase.setUserId(1L);
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase);
    }

    @Test
    void acceptsValidUtf8Text() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "MinIO 测试".getBytes(StandardCharsets.UTF_8));

        assertEquals("txt", service.validate(file, 1L, 10L));
    }

    @Test
    void acceptsDocxWithRequiredEntries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("types".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("document".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());

        assertEquals("docx", service.validate(file, 1L, 10L));
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", new byte[]{1, 2, 3});

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validate(file, 1L, 10L));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
    }

    @Test
    void rejectsFakePdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validate(file, 1L, 10L));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
    }

    @Test
    void rejectsBinaryContentDisguisedAsText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.txt", "text/plain", new byte[]{1, 0, 2});

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validate(file, 1L, 10L));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
    }

    @Test
    void rejectsKnowledgeBaseOwnedByAnotherUser() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validate(file, 2L, 10L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void rejectsMissingKnowledgeBase() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validate(file, 1L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
