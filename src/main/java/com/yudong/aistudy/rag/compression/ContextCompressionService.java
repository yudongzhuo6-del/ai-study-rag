package com.yudong.aistudy.rag.compression;

import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;

import java.util.List;

public interface ContextCompressionService {

    List<CompressedContext> compress(String question, List<RetrievedChunk> chunks);
}
