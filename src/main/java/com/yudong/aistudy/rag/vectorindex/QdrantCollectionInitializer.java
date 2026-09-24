package com.yudong.aistudy.rag.vectorindex;

import com.yudong.aistudy.config.properties.VectorIndexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class QdrantCollectionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionInitializer.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 1_000L;

    private final RestTemplate restTemplate;
    private final VectorIndexProperties properties;

    public QdrantCollectionInitializer(RestTemplate restTemplate, VectorIndexProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isInitializeOnStartup()) {
            log.info("Qdrant collection initialization skipped because it is disabled");
            return;
        }

        validateConfiguration();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                initializeCollection();
                return;
            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Cannot connect to Qdrant after " + MAX_ATTEMPTS + " attempts: "
                                    + properties.getBaseUrl(), e);
                }
                log.warn("Qdrant is not ready, retrying collection initialization ({}/{})",
                        attempt, MAX_ATTEMPTS);
                waitBeforeRetry();
            }
        }
    }

    private void initializeCollection() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(collectionUrl(), Map.class);
            validateExistingCollection(response.getBody());
            log.info("Qdrant collection is ready: name={}, dimension={}, distance={}",
                    properties.getCollectionName(), properties.getDimension(), properties.getDistance());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
            createCollection();
        }
    }

    private void createCollection() {
        Map<String, Object> vectors = Map.of(
                "size", properties.getDimension(),
                "distance", properties.getDistance()
        );
        restTemplate.put(collectionUrl(), Map.of("vectors", vectors));
        log.info("Created Qdrant collection: name={}, dimension={}, distance={}",
                properties.getCollectionName(), properties.getDimension(), properties.getDistance());
    }

    private void validateExistingCollection(Map<?, ?> response) {
        Object resultValue = response == null ? null : response.get("result");
        if (!(resultValue instanceof Map<?, ?> result)) {
            throw invalidResponse();
        }
        Object configValue = result.get("config");
        if (!(configValue instanceof Map<?, ?> config)) {
            throw invalidResponse();
        }
        Object paramsValue = config.get("params");
        if (!(paramsValue instanceof Map<?, ?> params)) {
            throw invalidResponse();
        }
        Object vectorsValue = params.get("vectors");
        if (!(vectorsValue instanceof Map<?, ?> vectors)) {
            throw invalidResponse();
        }

        int actualDimension = intValue(vectors.get("size"));
        String actualDistance = stringValue(vectors.get("distance"));
        if (actualDimension != properties.getDimension()) {
            throw new IllegalStateException("Qdrant collection dimension mismatch for '"
                    + properties.getCollectionName() + "': configured=" + properties.getDimension()
                    + ", actual=" + actualDimension);
        }
        if (!properties.getDistance().equalsIgnoreCase(actualDistance)) {
            throw new IllegalStateException("Qdrant collection distance mismatch for '"
                    + properties.getCollectionName() + "': configured=" + properties.getDistance()
                    + ", actual=" + actualDistance);
        }
    }

    private void validateConfiguration() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new IllegalStateException("rag.vector-index.base-url must not be blank");
        }
        if (properties.getCollectionName() == null || properties.getCollectionName().isBlank()) {
            throw new IllegalStateException("rag.vector-index.collection-name must not be blank");
        }
        if (properties.getDimension() <= 0) {
            throw new IllegalStateException("rag.vector-index.dimension must be positive");
        }
        if (properties.getDistance() == null || properties.getDistance().isBlank()) {
            throw new IllegalStateException("rag.vector-index.distance must not be blank");
        }
    }

    private String collectionUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/collections/" + properties.getCollectionName();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw invalidResponse();
    }

    private String stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw invalidResponse();
    }

    private IllegalStateException invalidResponse() {
        return new IllegalStateException("Qdrant returned an invalid collection configuration for '"
                + properties.getCollectionName() + "'");
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Qdrant", e);
        }
    }
}
