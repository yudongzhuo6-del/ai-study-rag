package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.keyword.bm25")
public class Bm25Properties {

    private double k1 = 1.5;

    private double b = 0.75;

    private double avgDocumentLength = 500.0;

    private long statisticsCacheTtlMillis = 300_000L;

    private double phraseBoost = 0.5;

    private double titleBoost = 0.3;
}
