package com.yudong.aistudy.rag.retriever;

import com.yudong.aistudy.config.properties.VectorIndexProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class VectorRetrieverRouterServiceImpl implements VectorRetrieverService {

    private static final Logger log = LoggerFactory.getLogger(VectorRetrieverRouterServiceImpl.class);

    private final InMemoryVectorRetrieverServiceImpl inMemoryVectorRetrieverService;

    private final IndexedVectorRetrieverServiceImpl indexedVectorRetrieverService;

    private final VectorIndexProperties vectorIndexProperties;

    public VectorRetrieverRouterServiceImpl(InMemoryVectorRetrieverServiceImpl inMemoryVectorRetrieverService,
                                            IndexedVectorRetrieverServiceImpl indexedVectorRetrieverService,
                                            VectorIndexProperties vectorIndexProperties) {
        this.inMemoryVectorRetrieverService = inMemoryVectorRetrieverService;
        this.indexedVectorRetrieverService = indexedVectorRetrieverService;
        this.vectorIndexProperties = vectorIndexProperties;
    }

    @Override
    public List<RetrievedChunk> retrieveTopK(RetrieverRequest request) {
        if (!vectorIndexProperties.isEnabled()) {
            log.info("Vector retrieve route: {}", VectorRetrieveSource.IN_MEMORY);
            return inMemoryVectorRetrieverService.retrieveTopK(request);
        }

        try {
            List<RetrievedChunk> results = indexedVectorRetrieverService.retrieveTopK(request);
            log.info("Vector retrieve route: {}, resultCount={}", VectorRetrieveSource.QDRANT, results.size());
            return results;
        } catch (Exception e) {
            log.warn("Vector retrieve route: QDRANT failed, fallback to IN_MEMORY.", e);
            return inMemoryVectorRetrieverService.retrieveTopK(request);
        }
    }
}
