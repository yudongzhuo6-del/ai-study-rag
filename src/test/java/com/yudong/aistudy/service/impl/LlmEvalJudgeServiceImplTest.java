package com.yudong.aistudy.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.config.properties.EvalJudgeProperties;
import com.yudong.aistudy.model.dto.eval.EvalJudgeResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmEvalJudgeServiceImplTest {

    private final LlmEvalJudgeServiceImpl service = new LlmEvalJudgeServiceImpl(
            new RestTemplate(),
            new EvalJudgeProperties(),
            new ObjectMapper()
    );

    @Test
    void parseResultParsesJsonAndCalculatesWeightedJudgeScore() {
        EvalJudgeResult result = service.parseResult("""
                {
                  "correctness": 5,
                  "completeness": 4,
                  "faithfulness": 3,
                  "relevance": 2,
                  "citationQuality": 1,
                  "reason": "ok"
                }
                """);

        result.setJudgeScore(service.calculateJudgeScore(result));

        assertEquals(5.0, result.getCorrectness());
        assertEquals(4.0, result.getCompleteness());
        assertEquals(3.0, result.getFaithfulness());
        assertEquals(2.0, result.getRelevance());
        assertEquals(1.0, result.getCitationQuality());
        assertEquals("ok", result.getReason());
        assertEquals(3.45, result.getJudgeScore(), 0.0001);
    }

    @Test
    void parseResultClampsScoresToZeroAndFive() {
        EvalJudgeResult result = service.parseResult("""
                ```json
                {
                  "correctness": 9,
                  "completeness": -2,
                  "faithfulness": 3,
                  "relevance": 5,
                  "citationQuality": 4,
                  "reason": "clamped"
                }
                ```
                """);

        assertEquals(5.0, result.getCorrectness());
        assertEquals(0.0, result.getCompleteness());
        assertEquals(3.0, result.getFaithfulness());
    }
}
