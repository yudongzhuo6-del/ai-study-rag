package com.yudong.aistudy.rag.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.config.properties.QueryRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmQueryRewriteServiceImpl implements QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(LlmQueryRewriteServiceImpl.class);

    private static final int MAX_QUERY_EXPANSION_RATIO = 5;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final QueryRewriteProperties queryRewriteProperties;

    public LlmQueryRewriteServiceImpl(ObjectMapper objectMapper,
                                      QueryRewriteProperties queryRewriteProperties) {
        this.objectMapper = objectMapper;
        this.queryRewriteProperties = queryRewriteProperties;
        this.restTemplate = buildRestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueryRewriteResult rewrite(String question) {
        String originalQuestion = question == null ? "" : question.trim();
        if (originalQuestion.isEmpty()) {
            throw new IllegalArgumentException("Question is empty.");
        }

        validateConfig();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(queryRewriteProperties.getLlmApiKey());

        Map<String, Object> requestBody = buildRequestBody(originalQuestion);
        Map<String, Object> response = restTemplate.postForObject(
                queryRewriteProperties.getLlmBaseUrl(),
                new HttpEntity<>(requestBody, headers),
                Map.class
        );

        String content = extractAssistantContent(response);
        String json = extractJson(content);
        QueryRewriteResult result = parseRewriteResult(json, originalQuestion);
        validateRewriteResult(result, originalQuestion);

        log.info("LLM query rewrite success: model={}, original={}", queryRewriteProperties.getLlmModel(), originalQuestion);
        return result;
    }

    private void validateConfig() {
        if (isBlank(queryRewriteProperties.getLlmBaseUrl())) {
            throw new IllegalStateException("Query rewrite LLM baseUrl is empty.");
        }

        if (isBlank(queryRewriteProperties.getLlmApiKey())) {
            throw new IllegalStateException("Query rewrite LLM apiKey is empty.");
        }

        if (isBlank(queryRewriteProperties.getLlmModel())) {
            throw new IllegalStateException("Query rewrite LLM model is empty.");
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(getTimeoutMillis());
        requestFactory.setReadTimeout(getTimeoutMillis());
        return new RestTemplate(requestFactory);
    }

    private int getTimeoutMillis() {
        if (queryRewriteProperties.getLlmTimeoutMillis() <= 0) {
            return 5000;
        }

        return queryRewriteProperties.getLlmTimeoutMillis();
    }

    private Map<String, Object> buildRequestBody(String question) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", queryRewriteProperties.getLlmModel());
        requestBody.put("temperature", 0.1);
        requestBody.put("messages", buildMessages(question));
        return requestBody;
    }

    private List<Map<String, String>> buildMessages(String question) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt()));
        messages.add(message("user", buildUserPrompt(question)));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildSystemPrompt() {
        return """
                You are a query rewrite component for a RAG retrieval system.
                Rewrite the user question into retrieval-friendly queries.
                Requirements:
                1. Output JSON only. Do not explain.
                2. Do not answer the question.
                3. Preserve entities, technical terms, product names, error codes, and domain words.
                4. vectorQuery is for semantic vector retrieval and may include synonyms.
                5. keywordQuery is for BM25 keyword retrieval and should contain important entities and phrases.
                6. expandedKeywords must contain at most 8 terms.
                7. If the question is already clear, only normalize it lightly.
                8. Keep the output language consistent with the user question.
                """;
    }

    private String buildUserPrompt(String question) {
        return """
                User question:
                %s

                Output JSON in this exact shape:
                {
                  "normalizedQuestion": "...",
                  "vectorQuery": "...",
                  "keywordQuery": "...",
                  "expandedKeywords": ["..."]
                }
                """.formatted(question);
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantContent(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("LLM response is null.");
        }

        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("LLM response choices is empty.");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("LLM response choice format is invalid.");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("LLM response message format is invalid.");
        }

        Object contentObject = messageMap.get("content");
        if (!(contentObject instanceof String content) || content.trim().isEmpty()) {
            throw new IllegalStateException("LLM response content is empty.");
        }

        return content;
    }

    private String extractJson(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM response does not contain JSON object.");
        }

        return text.substring(start, end + 1);
    }

    private QueryRewriteResult parseRewriteResult(String json, String originalQuestion) {
        try {
            QueryRewriteResult result = objectMapper.readValue(json, QueryRewriteResult.class);
            result.setOriginalQuestion(originalQuestion);
            result.setFallback(false);
            result.setFallbackReason(null);
            if (result.getExpandedKeywords() == null) {
                result.setExpandedKeywords(new ArrayList<>());
            }

            if (isBlank(result.getNormalizedQuestion())) {
                result.setNormalizedQuestion(originalQuestion);
            }

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM query rewrite JSON.", e);
        }
    }

    private void validateRewriteResult(QueryRewriteResult result, String originalQuestion) {
        if (result == null) {
            throw new IllegalStateException("Query rewrite result is null.");
        }

        if (isBlank(result.getVectorQuery())) {
            throw new IllegalStateException("LLM vectorQuery is empty.");
        }

        if (isBlank(result.getKeywordQuery())) {
            throw new IllegalStateException("LLM keywordQuery is empty.");
        }

        if (isQueryTooLong(result.getVectorQuery(), originalQuestion)
                || isQueryTooLong(result.getKeywordQuery(), originalQuestion)) {
            throw new IllegalStateException("LLM rewritten query is too long.");
        }

        int maxExpandedKeywords = getMaxExpandedKeywords();
        if (result.getExpandedKeywords().size() > maxExpandedKeywords) {
            result.setExpandedKeywords(new ArrayList<>(result.getExpandedKeywords().subList(0, maxExpandedKeywords)));
        }
    }

    private boolean isQueryTooLong(String query, String originalQuestion) {
        return query != null && query.length() > originalQuestion.length() * MAX_QUERY_EXPANSION_RATIO;
    }

    private int getMaxExpandedKeywords() {
        if (queryRewriteProperties.getMaxExpandedKeywords() <= 0) {
            return 8;
        }

        return queryRewriteProperties.getMaxExpandedKeywords();
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
