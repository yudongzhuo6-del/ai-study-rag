package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.retrieve")
public class RetrieveProperties {

    private int recallTopK = 50;

    private int keywordTopK = 50;

    private int hybridTopK = 80;

    private int finalTopK = 3;

    private double minFinalScore = 0.0;

    private int pageSize = 1000;

    private double vectorWeight = 0.6;

    private double keywordWeight = 0.4;
}
