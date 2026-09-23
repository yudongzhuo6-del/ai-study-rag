package com.yudong.aistudy.rag.rerank;

import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;

import java.util.List;

public interface RerankService {

    List<RetrievedChunk> rerank(String question, List<RetrievedChunk> retrievedChunks);
}
