package com.yudong.aistudy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    private Integer status;

    private String processError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public String resolveObjectKey() {
        return objectKey == null || objectKey.isBlank() ? fileUrl : objectKey;
    }

    public boolean isMinioStorage() {
        return "MINIO".equalsIgnoreCase(storageType) || objectKey != null && !objectKey.isBlank();
    }
}
