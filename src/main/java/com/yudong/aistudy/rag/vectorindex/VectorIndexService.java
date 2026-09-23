package com.yudong.aistudy.rag.vectorindex;

import com.yudong.aistudy.model.entity.DocumentChunk;

import java.util.List;

public interface VectorIndexService {

    void upsertChunk(DocumentChunk chunk, double[] vector);

    List<VectorIndexSearchResult> search(double[] queryVector, int topK, List<Long> documentIds);

    void deleteByDocumentId(Long documentId);
}
