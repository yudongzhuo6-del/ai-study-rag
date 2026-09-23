package com.yudong.aistudy.service;

import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.config.properties.DocumentUploadProperties;
import com.yudong.aistudy.mapper.KnowledgeBaseMapper;
import com.yudong.aistudy.model.entity.KnowledgeBase;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentUploadValidationService {

    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf", "application/octet-stream");
    private static final Set<String> DOCX_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/octet-stream"
    );
    private static final Set<String> TEXT_CONTENT_TYPES = Set.of(
            "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream"
    );

    private final DocumentUploadProperties properties;//调用配置类
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public DocumentUploadValidationService(DocumentUploadProperties properties,
                                           KnowledgeBaseMapper knowledgeBaseMapper) {
        this.properties = properties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    public String validate(MultipartFile file, Long userId, Long knowledgeBaseId) {
        validateOwner(userId, knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "File cannot be empty");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 50 MB upload limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        String extension = extensionOf(filename);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type; allowed types: txt, md, docx, pdf");
        }

        validateContentType(file.getContentType(), extension);
        validateSignature(file, extension);
        return extension;
    }

    public String calculateSha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) >= 0) {
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return toHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot calculate uploaded file hash");
        }
    }

    private void validateOwner(Long userId, Long knowledgeBaseId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userId is required and must be positive");
        }
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "knowledgeBaseId is required and must be positive");
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Knowledge base not found");
        }
        if (!userId.equals(knowledgeBase.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Knowledge base does not belong to this user");
        }
    }

    private void validateContentType(String contentType, String extension) {
        String normalized = contentType == null ? "application/octet-stream"
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = switch (extension) {
            case "pdf" -> PDF_CONTENT_TYPES;
            case "docx" -> DOCX_CONTENT_TYPES;
            default -> TEXT_CONTENT_TYPES;
        };
        if (!allowed.contains(normalized)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File content type does not match its extension");
        }
    }

    private void validateSignature(MultipartFile file, String extension) {
        try {
            switch (extension) {
                case "pdf" -> validatePdf(file);
                case "docx" -> validateDocx(file);
                default -> validateUtf8Text(file);
            }
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot read uploaded file");
        }
    }

    private void validatePdf(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(5);
            if (signature.length != 5 || !"%PDF-".equals(new String(signature, StandardCharsets.US_ASCII))) {
                throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid PDF file content");
            }
        }
    }

    private void validateDocx(MultipartFile file) throws IOException {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                } else if ("word/document.xml".equals(entry.getName())) {
                    hasDocument = true;
                }
                if (hasContentTypes && hasDocument) {
                    return;
                }
            }
        }
        throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid DOCX file content");
    }

    private void validateUtf8Text(MultipartFile file) throws IOException {
        byte[] content;
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            content = output.toByteArray();
        }
        for (byte value : content) {
            if (value == 0) {
                throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Text file contains binary content");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException e) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Text file must use UTF-8 encoding");
        }
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
