package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rag.query-rewrite")
public class QueryRewriteProperties {

    private boolean enabled = true;

    private boolean expandKeywords = true;

    private boolean llmEnabled = false;

    private String llmBaseUrl;

    private String llmApiKey;

    private String llmModel;

    private int llmTimeoutMillis = 5000;

    private int maxExpandedKeywords = 8;

    private Map<String, List<String>> synonyms = new LinkedHashMap<>();
}
