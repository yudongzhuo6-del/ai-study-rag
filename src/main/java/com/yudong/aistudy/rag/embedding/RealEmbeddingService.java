package com.yudong.aistudy.rag.embedding;

import com.yudong.aistudy.config.properties.OpenAiEmbeddingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Component
public class RealEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RealEmbeddingService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OpenAiEmbeddingProperties openAiEmbeddingProperties;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${rag.embedding.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${rag.embedding.cache-ttl-days:30}")
    private long cacheTtlDays;

    @Override
    public String embed(String text) {
        String cacheKey = buildCacheKey(text);
        String cachedEmbedding = getCachedEmbedding(cacheKey);
        if (cachedEmbedding != null) {
            return cachedEmbedding;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiEmbeddingProperties.getModel());
        requestBody.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiEmbeddingProperties.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<OpenAiEmbeddingResponse> response = restTemplate.exchange(
                openAiEmbeddingProperties.getBaseUrl(),
                HttpMethod.POST,
                entity,
                OpenAiEmbeddingResponse.class
        );

        OpenAiEmbeddingResponse body = response.getBody();
        if (body == null || body.getData() == null || body.getData().isEmpty()) {
            return "";
        }

        OpenAiEmbeddingData data = body.getData().get(0);
        if (data == null || data.getEmbedding() == null || data.getEmbedding().isEmpty()) {
            return "";
        }

        String embedding = toVectorString(data.getEmbedding());
        cacheEmbedding(cacheKey, embedding);
        return embedding;
    }

    private String toVectorString(List<Double> embedding) {
        StringBuilder sb = new StringBuilder();

        for (Double value : embedding) {
            sb.append(value).append(",");
        }

        return sb.toString();
    }

    private String getCachedEmbedding(String cacheKey) {
        if (!cacheEnabled || cacheKey == null) {
            return null;
        }

        try {
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Read embedding cache failed, fallback to remote embedding.", e);
            return null;
        }
    }

    private void cacheEmbedding(String cacheKey, String embedding) {
        if (!cacheEnabled || cacheKey == null || embedding == null || embedding.isEmpty()) {
            return;
        }

        try {
            Duration ttl = Duration.ofDays(Math.max(1, cacheTtlDays));
            stringRedisTemplate.opsForValue().set(cacheKey, embedding, ttl);
        } catch (Exception e) {
            log.warn("Write embedding cache failed, ignore cache write.", e);
        }
    }

    private String buildCacheKey(String text) {
        if (!cacheEnabled || text == null || text.isEmpty()) {
            return null;
        }

        return "rag:embedding:"
                + openAiEmbeddingProperties.getModel()
                + ":"
                + sha256(text);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    public static class OpenAiEmbeddingResponse {

        private List<OpenAiEmbeddingData> data;

        public List<OpenAiEmbeddingData> getData() {
            return data;
        }

        public void setData(List<OpenAiEmbeddingData> data) {
            this.data = data;
        }
    }

    public static class OpenAiEmbeddingData {

        private List<Double> embedding;

        public List<Double> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }
    }
}
