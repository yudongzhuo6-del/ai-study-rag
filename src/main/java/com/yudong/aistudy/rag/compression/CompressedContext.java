package com.yudong.aistudy.rag.compression;

import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import lombok.Data;

@Data
public class CompressedContext {

    private RetrievedChunk retrievedChunk;

    private String compressedContent;

    private Double compressionRatio;
}
