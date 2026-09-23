package com.yudong.aistudy.rag.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryRewriteSynonymProvider {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteSynonymProvider.class);

    private static final String SYNONYM_FILE_PATH = "rag/query-rewrite-synonyms.json";

    private final ObjectMapper objectMapper;

    private final Map<String, List<String>> synonymMap;

    public QueryRewriteSynonymProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.synonymMap = loadSynonyms();
    }

    public Map<String, List<String>> getSynonymMap() {
        return synonymMap;
    }

    private Map<String, List<String>> loadSynonyms() {
        try {
            ClassPathResource resource = new ClassPathResource(SYNONYM_FILE_PATH);
            if (!resource.exists()) {
                log.warn("Query rewrite synonym file not found, use default synonyms: {}", SYNONYM_FILE_PATH);
                return defaultSynonymMap();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, List<String>> loadedSynonyms = objectMapper.readValue(
                        inputStream,
                        new TypeReference<LinkedHashMap<String, List<String>>>() {
                        }
                );
                if (loadedSynonyms == null || loadedSynonyms.isEmpty()) {
                    log.warn("Query rewrite synonym file is empty, use default synonyms: {}", SYNONYM_FILE_PATH);
                    return defaultSynonymMap();
                }

                log.info("Loaded query rewrite synonyms: path={}, size={}", SYNONYM_FILE_PATH, loadedSynonyms.size());
                return loadedSynonyms;
            }
        } catch (Exception e) {
            log.warn("Failed to load query rewrite synonyms, use default synonyms: {}", SYNONYM_FILE_PATH, e);
            return defaultSynonymMap();
        }
    }

    private Map<String, List<String>> defaultSynonymMap() {
        Map<String, List<String>> synonyms = new LinkedHashMap<>();
        synonyms.put("redis", Arrays.asList("redis", "\u7f13\u5b58", "key"));
        synonyms.put("\u7f13\u5b58\u51fb\u7a7f", Arrays.asList(
                "\u7f13\u5b58\u51fb\u7a7f", "\u70ed\u70b9key", "\u4e92\u65a5\u9501", "\u903b\u8f91\u8fc7\u671f"
        ));
        synonyms.put("\u7f13\u5b58\u7a7f\u900f", Arrays.asList(
                "\u7f13\u5b58\u7a7f\u900f", "\u5e03\u9686\u8fc7\u6ee4\u5668", "\u7f13\u5b58\u7a7a\u503c"
        ));
        synonyms.put("\u7f13\u5b58\u96ea\u5d29", Arrays.asList(
                "\u7f13\u5b58\u96ea\u5d29", "\u8fc7\u671f\u65f6\u95f4\u968f\u673a\u5316",
                "\u591a\u7ea7\u7f13\u5b58", "\u9650\u6d41\u964d\u7ea7"
        ));
        synonyms.put("mysql", Arrays.asList("mysql", "\u6570\u636e\u5e93", "\u7d22\u5f15", "\u4e8b\u52a1"));
        synonyms.put("rag", Arrays.asList("rag", "\u68c0\u7d22\u589e\u5f3a\u751f\u6210", "embedding", "rerank", "chunk"));
        synonyms.put("\u5411\u91cf\u68c0\u7d22", Arrays.asList("\u5411\u91cf\u68c0\u7d22", "embedding", "\u76f8\u4f3c\u5ea6", "qdrant"));
        synonyms.put("\u91cd\u6392\u5e8f", Arrays.asList("\u91cd\u6392\u5e8f", "rerank", "finalScore"));
        return synonyms;
    }
}
