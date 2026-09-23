package com.yudong.aistudy.model.dto.retrieval;

import com.yudong.aistudy.model.entity.DocumentChunk;
import lombok.Data;

@Data
public class RetrievedChunk {

    private DocumentChunk chunk;

    private Double similarityScore;

    private Double keywordScore;

    private Double finalScore;

    private String retrieveSource;

    private String vectorRetrieveSource;

    private Double keywordRecallScore;

    private Double hybridRecallScore;
}
