package com.yudong.aistudy.rag.retriever;

import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;

import java.util.List;

public interface HybridRetrieverService {

    List<RetrievedChunk> retrieve(RetrieverRequest request);
}
