package com.yudong.aistudy.config.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEmbeddingProperties {

    @Value("${openai.embedding.base-url}")
    private String baseUrl;

    @Value("${openai.embedding.api-key}")
    private String apiKey;

    @Value("${openai.embedding.model}")
    private String model;

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
