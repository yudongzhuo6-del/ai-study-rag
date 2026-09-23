package com.yudong.aistudy.model.vo.document;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentProcessStatusVO {

    private Long documentId;
    private Integer status;
    private String statusName;
    private String processError;
    private LocalDateTime updateTime;
}
