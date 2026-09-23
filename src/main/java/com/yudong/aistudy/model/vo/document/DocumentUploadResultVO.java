package com.yudong.aistudy.model.vo.document;

import lombok.Data;

@Data
public class DocumentUploadResultVO {

    private Long documentId;
    private String fileName;
    private String objectKey;
    private Integer status;
}
