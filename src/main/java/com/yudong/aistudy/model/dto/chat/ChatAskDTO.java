package com.yudong.aistudy.model.dto.chat;

import lombok.Data;

import java.util.List;

@Data
public class ChatAskDTO {

    private String question;

    private Long userId;

    private Long knowledgeBaseId;

    private List<Long> knowledgeBaseIds;

    private Long documentId;

    private List<Long> documentIds;

    private String documentType;

    private List<String> documentTypes;
}
