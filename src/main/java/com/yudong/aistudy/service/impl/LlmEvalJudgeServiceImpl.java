package com.yudong.aistudy.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.config.properties.EvalJudgeProperties;
import com.yudong.aistudy.model.dto.eval.EvalJudgeRequest;
import com.yudong.aistudy.model.dto.eval.EvalJudgeResult;
import com.yudong.aistudy.model.dto.llm.LlmChatRequestDTO;
import com.yudong.aistudy.model.dto.llm.LlmChatResponseDTO;
import com.yudong.aistudy.model.dto.llm.LlmMessageDTO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import com.yudong.aistudy.service.EvalJudgeService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class LlmEvalJudgeServiceImpl implements EvalJudgeService {

    private final RestTemplate restTemplate;

    private final EvalJudgeProperties evalJudgeProperties;

    private final ObjectMapper objectMapper;

    public LlmEvalJudgeServiceImpl(RestTemplate restTemplate,
                                   EvalJudgeProperties evalJudgeProperties,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.evalJudgeProperties = evalJudgeProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvalJudgeResult judge(EvalJudgeRequest request) {
        if (!evalJudgeProperties.isEnabled()) {
            return null;
        }

        String prompt = buildPrompt(request);
        LlmChatRequestDTO requestDTO = new LlmChatRequestDTO();
        requestDTO.setModel(evalJudgeProperties.getModel());
        requestDTO.setMessages(Arrays.asList(
                new LlmMessageDTO("system", "You are a strict RAG evaluation judge. Output valid JSON only."),
                new LlmMessageDTO("user", prompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(evalJudgeProperties.getApiKey());

        ResponseEntity<LlmChatResponseDTO> response = restTemplate.exchange(
                evalJudgeProperties.getBaseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestDTO, headers),
                LlmChatResponseDTO.class
        );

        String content = extractContent(response.getBody());
        EvalJudgeResult result = parseResult(content);
        result.setRawResponse(content);
        result.setJudgeScore(calculateJudgeScore(result));
        return result;
    }

    private String buildPrompt(EvalJudgeRequest request) {
        return """
                Evaluate this RAG answer objectively.

                Score each dimension from 0 to 5. Decimals are allowed.

                Dimensions:
                correctness: whether the answer is factually correct and consistent with the expected answer.
                completeness: whether the answer covers the expected answer and required keywords.
                faithfulness: whether the answer is fully supported by the retrieved sources.
                relevance: whether the answer directly answers the question.
                citationQuality: whether citations are reasonable and key claims cite valid sources.

                Return JSON only, with this exact schema:
                {
                  "correctness": 0,
                  "completeness": 0,
                  "faithfulness": 0,
                  "relevance": 0,
                  "citationQuality": 0,
                  "reason": "short reason"
                }

                Question:
                %s

                Expected answer:
                %s

                Required keywords:
                %s

                Retrieved sources:
                %s

                Actual answer:
                %s

                Citation check:
                citationValid=%s
                missingCitation=%s
                invalidCitations=%s
                """.formatted(
                blankToEmpty(request.getQuestion()),
                blankToEmpty(request.getExpectedAnswer()),
                blankToEmpty(request.getRequiredKeywords()),
                formatSources(request.getSources()),
                blankToEmpty(request.getActualAnswer()),
                request.isCitationValid(),
                request.isMissingCitation(),
                request.getInvalidCitations()
        );
    }

    private String formatSources(List<ChatSourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (ChatSourceVO source : sources) {
            if (source == null) {
                continue;
            }

            builder.append("[")
                    .append(source.getCitationIndex())
                    .append("]\n")
                    .append("documentId: ")
                    .append(source.getDocumentId())
                    .append("\n")
                    .append("chunkIndex: ")
                    .append(source.getChunkIndex())
                    .append("\n")
                    .append("content:\n")
                    .append(firstNonBlank(source.getCompressedContent(), source.getContent()))
                    .append("\n\n");
        }

        return builder.toString();
    }

    private String extractContent(LlmChatResponseDTO response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().getContent() == null) {
            throw new IllegalStateException("Eval judge response is empty");
        }

        return response.getChoices().get(0).getMessage().getContent();
    }

    EvalJudgeResult parseResult(String rawResponse) {
        String json = extractJson(rawResponse);
        try {
            JsonNode node = objectMapper.readTree(json);
            EvalJudgeResult result = new EvalJudgeResult();
            result.setCorrectness(readScore(node, "correctness"));
            result.setCompleteness(readScore(node, "completeness"));
            result.setFaithfulness(readScore(node, "faithfulness"));
            result.setRelevance(readScore(node, "relevance"));
            result.setCitationQuality(readScore(node, "citationQuality"));
            JsonNode reason = node.get("reason");
            if (reason != null && !reason.isNull()) {
                result.setReason(reason.asText());
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Eval judge JSON parse failed", e);
        }
    }

    private String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalStateException("Eval judge response is blank");
        }

        String text = rawResponse.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Eval judge response does not contain JSON object");
        }

        return text.substring(start, end + 1);
    }

    private Double readScore(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isNumber()) {
            return 0.0;
        }

        return clampScore(value.asDouble());
    }

    Double calculateJudgeScore(EvalJudgeResult result) {
        return result.getCorrectness() * 0.30
                + result.getFaithfulness() * 0.25
                + result.getCompleteness() * 0.20
                + result.getRelevance() * 0.15
                + result.getCitationQuality() * 0.10;
    }

    private Double clampScore(double value) {
        return Math.max(0.0, Math.min(5.0, value));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }

        return second == null ? "" : second;
    }
}
