package com.yudong.aistudy.rag.rerank;

import com.yudong.aistudy.config.properties.RerankProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelRerankServiceImpl implements RerankService {

    private final RerankProperties rerankProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    public ModelRerankServiceImpl(RerankProperties rerankProperties) {
        this.rerankProperties = rerankProperties;
    }

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> retrievedChunks) {
        Map<Integer, Double> scoreMap = callModelRerankApi(question, retrievedChunks);

        for (int i = 0; i < retrievedChunks.size(); i++) {
            RetrievedChunk retrievedChunk = retrievedChunks.get(i);
            Double relevanceScore = scoreMap.get(i);

            if (relevanceScore == null) {
                throw new IllegalStateException("Missing rerank score for document index: " + i);
            }

            // Model rerank does not produce keywordScore; keep it null to distinguish it from rule-based scoring.
            retrievedChunk.setKeywordScore(null);
            retrievedChunk.setFinalScore(relevanceScore);
        }

        retrievedChunks.sort((a, b) -> Double.compare(
                b.getFinalScore(),
                a.getFinalScore()
        ));

        return retrievedChunks;
    }

    private Map<Integer, Double> callModelRerankApi(String question, List<RetrievedChunk> retrievedChunks) {
        List<String> documents = new ArrayList<>();
        for (RetrievedChunk retrievedChunk : retrievedChunks) {
            documents.add(retrievedChunk.getChunk().getContent());
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", question);
        requestBody.put("documents", documents);
        requestBody.put("model", rerankProperties.getModel());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (rerankProperties.getApiKey() != null && !rerankProperties.getApiKey().trim().isEmpty()) {
            headers.setBearerAuth(rerankProperties.getApiKey());
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                rerankProperties.getBaseUrl(),
                requestEntity,
                Map.class
        );

        return parseScoreMap(response.getBody());
    }

    private Map<Integer, Double> parseScoreMap(Map responseBody) {
        Map<Integer, Double> scoreMap = new HashMap<>();
        if (responseBody == null) {
            return scoreMap;
        }

        Object resultsObject = responseBody.get("results");
        if (!(resultsObject instanceof List<?> results)) {
            return scoreMap;
        }

        for (Object resultObject : results) {
            if (!(resultObject instanceof Map<?, ?> result)) {
                continue;
            }

            Object indexObject = result.get("index");
            Object scoreObject = result.get("relevance_score");
            if (!(indexObject instanceof Number index) || !(scoreObject instanceof Number score)) {
                continue;
            }

            scoreMap.put(index.intValue(), score.doubleValue());
        }

        return scoreMap;
    }
}
