package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.splitter")
public class SplitterProperties {

    private boolean semanticEnabled = true;

    private double semanticThreshold = 0.65;

    private int chunkSize = 500;

    private int overlap = 100;

    private int minParagraphLength = 80;

    private int maxParagraphLength = 500;
}
