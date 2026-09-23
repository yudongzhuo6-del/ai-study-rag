package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.rerank")
public class RerankProperties {

    private boolean enabled = false;

    private String baseUrl;

    private String apiKey;

    private String model;
}
