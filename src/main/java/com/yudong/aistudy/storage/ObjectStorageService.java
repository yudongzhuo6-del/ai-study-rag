package com.yudong.aistudy.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface ObjectStorageService {

    String upload(MultipartFile file, Long userId, Long knowledgeBaseId);

    InputStream download(String objectKey);

    void delete(String objectKey);

    String generatePresignedUrl(String objectKey);
}
