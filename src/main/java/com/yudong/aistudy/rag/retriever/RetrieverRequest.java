package com.yudong.aistudy.rag.retriever;

import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import lombok.Data;

@Data
public class RetrieverRequest {

    private String question;

    private double[] queryVector;

    private int vectorTopK;

    private int keywordTopK;

    private int hybridTopK;

    private Long documentId;

    private RetrieveFilter filter;
}
