package com.yudong.aistudy.rag.query;

import com.yudong.aistudy.config.properties.QueryRewriteProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuleBasedQueryRewriteServiceImpl implements QueryRewriteService {

    private static final int MAX_QUERY_EXPANSION_RATIO = 3;

    private static final String REASON_DISABLED = "DISABLED";

    private static final String REASON_ORIGINAL_EMPTY = "ORIGINAL_EMPTY";

    private static final String REASON_NORMALIZED_EMPTY = "NORMALIZED_EMPTY";

    private static final String REASON_EXPANDED_KEYWORDS_TOO_MANY = "EXPANDED_KEYWORDS_TOO_MANY";

    private static final String REASON_QUERY_TOO_LONG = "QUERY_TOO_LONG";

    private static final String REASON_GENERIC_ONLY = "GENERIC_ONLY";

    private static final List<String> QUESTION_NOISE_WORDS = Arrays.asList(
            "什么", "怎么", "如何", "为什么",
            "是否", "请问", "这个", "那个",
            "有哪些", "多少", "以及", "的",
            "是", "吗", "呢", "啊"
    );

    private static final Set<String> GENERIC_WORDS = new LinkedHashSet<>(Arrays.asList(
            "缓存", "key", "数据库", "索引",
            "事务", "相似度", "问题", "内容"
    ));

    private final QueryRewriteProperties queryRewriteProperties;

    private final QueryRewriteSynonymProvider queryRewriteSynonymProvider;

    public RuleBasedQueryRewriteServiceImpl(QueryRewriteProperties queryRewriteProperties,
                                            QueryRewriteSynonymProvider queryRewriteSynonymProvider) {
        this.queryRewriteProperties = queryRewriteProperties;
        this.queryRewriteSynonymProvider = queryRewriteSynonymProvider;
    }

    @Override
    public QueryRewriteResult rewrite(String question) {
        String originalQuestion = question == null ? "" : question.trim();
        String normalizedQuestion = normalizeQuestion(originalQuestion);
        List<String> expandedKeywords = expandKeywords(normalizedQuestion);
        String expandedQuery = buildExpandedQuery(normalizedQuestion, expandedKeywords);
        String fallbackReason = getFallbackReason(originalQuestion, normalizedQuestion, expandedQuery, expandedKeywords);

        QueryRewriteResult result = new QueryRewriteResult();
        result.setOriginalQuestion(originalQuestion);
        result.setNormalizedQuestion(normalizedQuestion);

        if (fallbackReason != null) {
            result.setVectorQuery(originalQuestion);
            result.setKeywordQuery(originalQuestion);
            result.setExpandedKeywords(new ArrayList<>());
            result.setFallback(true);
            result.setFallbackReason(fallbackReason);
            return result;
        }

        result.setVectorQuery(expandedQuery);
        result.setKeywordQuery(expandedQuery);
        result.setExpandedKeywords(expandedKeywords);
        result.setFallback(false);
        result.setFallbackReason(null);
        return result;
    }

    private String getFallbackReason(String originalQuestion,
                                     String normalizedQuestion,
                                     String expandedQuery,
                                     List<String> expandedKeywords) {
        if (!queryRewriteProperties.isEnabled()) {
            return REASON_DISABLED;
        }

        if (originalQuestion == null || originalQuestion.trim().isEmpty()) {
            return REASON_ORIGINAL_EMPTY;
        }

        if (normalizedQuestion == null || normalizedQuestion.trim().isEmpty()) {
            return REASON_NORMALIZED_EMPTY;
        }

        if (expandedKeywords != null && expandedKeywords.size() > getMaxExpandedKeywords()) {
            return REASON_EXPANDED_KEYWORDS_TOO_MANY;
        }

        if (expandedQuery != null
                && expandedQuery.length() > originalQuestion.length() * MAX_QUERY_EXPANSION_RATIO) {
            return REASON_QUERY_TOO_LONG;
        }

        if (containsOnlyGenericWords(normalizedQuestion)) {
            return REASON_GENERIC_ONLY;
        }

        return null;
    }

    private boolean containsOnlyGenericWords(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.trim().isEmpty()) {
            return true;
        }

        String[] words = normalizedQuestion.split("\\s+");
        for (String word : words) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty() && !GENERIC_WORDS.contains(trimmed)) {
                return false;
            }
        }

        return true;
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "";
        }

        String normalized = question.toLowerCase()
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[?？!！,，。；;：:]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String noiseWord : QUESTION_NOISE_WORDS) {
            normalized = normalized.replace(noiseWord, " ");
        }

        return normalized.replaceAll("\\s+", " ").trim();
    }

    private List<String> expandKeywords(String normalizedQuestion) {
        if (!queryRewriteProperties.isExpandKeywords() || normalizedQuestion == null || normalizedQuestion.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> keywords = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : getSynonymMap().entrySet()) {
            if (normalizedQuestion.contains(entry.getKey())) {
                keywords.addAll(entry.getValue());
            }
        }

        int maxExpandedKeywords = getMaxExpandedKeywords();
        List<String> result = new ArrayList<>(keywords);
        if (result.size() > maxExpandedKeywords) {
            return result.subList(0, maxExpandedKeywords);
        }

        return result;
    }

    private String buildExpandedQuery(String normalizedQuestion, List<String> expandedKeywords) {
        Set<String> queryParts = new LinkedHashSet<>();
        if (normalizedQuestion != null && !normalizedQuestion.trim().isEmpty()) {
            queryParts.add(normalizedQuestion.trim());
        }

        if (expandedKeywords != null) {
            queryParts.addAll(expandedKeywords);
        }

        if (queryParts.isEmpty()) {
            return "";
        }

        return String.join(" ", queryParts);
    }

    private Map<String, List<String>> getSynonymMap() {
        if (queryRewriteProperties.getSynonyms() == null || queryRewriteProperties.getSynonyms().isEmpty()) {
            return queryRewriteSynonymProvider.getSynonymMap();
        }

        return queryRewriteProperties.getSynonyms();
    }

    private int getMaxExpandedKeywords() {
        if (queryRewriteProperties.getMaxExpandedKeywords() <= 0) {
            return 8;
        }

        return queryRewriteProperties.getMaxExpandedKeywords();
    }
}
