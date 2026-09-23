package com.yudong.aistudy.model.dto.document;

import lombok.Data;

@Data
public class DocumentAddDTO {

    private String name;

    private String fileUrl;

    private String storageType;

    private String objectKey;

    private String bucketName;

    private Long fileSize;

    private String contentType;

    private String fileHash;

    private String type;

    private Long userId;

    private Long knowledgeBaseId;
}
