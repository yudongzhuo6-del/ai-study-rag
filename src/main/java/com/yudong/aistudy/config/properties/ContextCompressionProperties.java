package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.context-compression")
public class ContextCompressionProperties {

    private boolean enabled = true;

    private int maxSentencesPerChunk = 4;

    private int maxCharsPerChunk = 800;

    private int fallbackChars = 200;

    private int minKeywordLength = 2;
}
