package com.yudong.aistudy.model.vo.document;

import lombok.Data;

@Data
public class DocumentDeleteResultVO {

    private Long documentId;

    private Boolean documentDeleted;

    private Integer chunkDeletedCount;

    private Boolean fileDeleted;

    private String filePath;

    private String objectKey;
}
