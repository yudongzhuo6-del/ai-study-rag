package com.yudong.aistudy.config.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvalJudgeProperties {

    @Value("${eval.judge.enabled:true}")
    private boolean enabled;

    @Value("${eval.judge.base-url:${llm.base-url}}")
    private String baseUrl;

    @Value("${eval.judge.api-key:${llm.api-key}}")
    private String apiKey;

    @Value("${eval.judge.model:${llm.model}}")
    private String model;

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }
}
