package com.yudong.aistudy.model.vo.chat;

import lombok.Data;

@Data
public class ChatSourceVO {

    private Integer citationIndex;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private String compressedContent;

    private Double similarityScore;

    private Double keywordScore;

    private Double finalScore;

    private String retrieveSource;

    private String vectorRetrieveSource;

    private Double keywordRecallScore;

    private Double hybridRecallScore;
}
