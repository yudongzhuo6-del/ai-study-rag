package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.chunk-dedup")
public class ChunkDedupProperties {

    private boolean enabled = true;

    private boolean crossDocumentEnabled = true;

    private boolean bodyHashEnabled = true;

    private boolean nearDuplicateEnabled = false;

    private double nearDuplicateThreshold = 0.9;

    private int shingleSize = 5;

    private int minLength = 30;
}
