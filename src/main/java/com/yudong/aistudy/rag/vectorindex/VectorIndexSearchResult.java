package com.yudong.aistudy.rag.vectorindex;

import lombok.Data;

@Data
public class VectorIndexSearchResult {

    private Long chunkId;

    private Double score;
}
