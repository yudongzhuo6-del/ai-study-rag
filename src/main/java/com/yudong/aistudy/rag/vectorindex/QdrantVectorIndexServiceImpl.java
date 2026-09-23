package com.yudong.aistudy.rag.vectorindex;

import com.yudong.aistudy.config.properties.VectorIndexProperties;
import com.yudong.aistudy.model.entity.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QdrantVectorIndexServiceImpl implements VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorIndexServiceImpl.class);

    private final RestTemplate restTemplate;

    private final VectorIndexProperties vectorIndexProperties;

    public QdrantVectorIndexServiceImpl(RestTemplate restTemplate,
                                        VectorIndexProperties vectorIndexProperties) {
        this.restTemplate = restTemplate;
        this.vectorIndexProperties = vectorIndexProperties;
    }

    @Override
    public void upsertChunk(DocumentChunk chunk, double[] vector) {
        if (!vectorIndexProperties.isEnabled() || chunk == null || chunk.getId() == null || vector == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", chunk.getDocumentId());
        payload.put("chunkIndex", chunk.getChunkIndex());

        Map<String, Object> point = new HashMap<>();
        point.put("id", chunk.getId());
        point.put("vector", toList(vector));
        point.put("payload", payload);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("points", List.of(point));

        restTemplate.put(buildUrl("/points"), requestBody);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<VectorIndexSearchResult> search(double[] queryVector, int topK, List<Long> documentIds) {
        if (!vectorIndexProperties.isEnabled() || queryVector == null || queryVector.length == 0 || topK <= 0) {
            return new ArrayList<>();
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("vector", toList(queryVector));
        requestBody.put("limit", topK);
        requestBody.put("with_payload", true);

        if (documentIds != null && !documentIds.isEmpty()) {
            requestBody.put("filter", buildDocumentFilter(documentIds));
        }

        Map<String, Object> response = restTemplate.postForObject(
                buildUrl("/points/search"),
                requestBody,
                Map.class
        );
        if (response == null || !(response.get("result") instanceof List<?> resultItems)) {
            log.info(
                    "Qdrant vector search: collection={}, documentIds={}, topK={}, resultCount=0",
                    vectorIndexProperties.getCollectionName(),
                    documentIds,
                    topK
            );
            return new ArrayList<>();
        }

        List<VectorIndexSearchResult> results = new ArrayList<>();
        for (Object item : resultItems) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }

            Long chunkId = toLong(itemMap.get("id"));
            Double score = toDouble(itemMap.get("score"));
            if (chunkId == null || score == null) {
                continue;
            }

            VectorIndexSearchResult result = new VectorIndexSearchResult();
            result.setChunkId(chunkId);
            result.setScore(score);
            results.add(result);
        }

        log.info(
                "Qdrant vector search: collection={}, documentIds={}, topK={}, resultCount={}",
                vectorIndexProperties.getCollectionName(),
                documentIds,
                topK,
                results.size()
        );
        return results;
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (!vectorIndexProperties.isEnabled() || documentId == null) {
            return;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("filter", buildDocumentFilter(List.of(documentId)));

        restTemplate.postForObject(buildUrl("/points/delete"), requestBody, Map.class);
    }

    private String buildUrl(String path) {
        String baseUrl = vectorIndexProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/collections/" + vectorIndexProperties.getCollectionName() + path;
    }

    private Map<String, Object> buildDocumentFilter(List<Long> documentIds) {
        Map<String, Object> match = new HashMap<>();
        match.put("any", documentIds);

        Map<String, Object> condition = new HashMap<>();
        condition.put("key", "documentId");
        condition.put("match", match);

        Map<String, Object> filter = new HashMap<>();
        filter.put("must", List.of(condition));
        return filter;
    }

    private List<Double> toList(double[] vector) {
        List<Double> values = new ArrayList<>();
        for (double value : vector) {
            values.add(value);
        }

        return values;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }
}
