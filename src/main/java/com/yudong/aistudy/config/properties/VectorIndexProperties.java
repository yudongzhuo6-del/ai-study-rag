package com.yudong.aistudy.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.vector-index")
public class VectorIndexProperties {

    private boolean enabled = false;

    private String baseUrl = "http://localhost:6333";

    private String collectionName = "document_chunks";

    private int dimension = 1024;
}
