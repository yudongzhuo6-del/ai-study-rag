package com.yudong.aistudy.rag.keyword;

import com.yudong.aistudy.config.properties.Bm25Properties;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class Bm25Scorer {

    private static final String[] QUERY_NOISE_WORDS = {
            "\u4ec0\u4e48",
            "\u600e\u4e48",
            "\u5982\u4f55",
            "\u4e3a\u4ec0\u4e48",
            "\u662f\u5426",
            "\u8bf7\u95ee",
            "\u8fd9\u4e2a",
            "\u90a3\u4e2a",
            "\u6709\u54ea\u4e9b",
            "\u591a\u5c11",
            "\u4ee5\u53ca",
            "\u548c",
            "\u7684",
            "\u662f"
    };

    private final Bm25Properties bm25Properties;

    private final Bm25StatisticsService bm25StatisticsService;

    public Bm25Scorer(Bm25Properties bm25Properties,
                      Bm25StatisticsService bm25StatisticsService) {
        this.bm25Properties = bm25Properties;
        this.bm25StatisticsService = bm25StatisticsService;
    }

    public double score(List<String> terms, String content) {
        return score(terms, content, null);
    }

    public double score(List<String> terms, String content, Long documentId) {
        return score(terms, content, null, documentId);
    }

    public double score(List<String> terms, String content, String question, Long documentId) {
        List<Long> documentIds = documentId == null ? Collections.emptyList() : Collections.singletonList(documentId);
        return score(terms, content, question, documentIds, null);
    }

    public double score(List<String> terms, String content, String question, List<Long> documentIds, Long knowledgeBaseId) {
        if (terms == null || terms.isEmpty() || content == null || content.trim().isEmpty()) {
            return 0.0;
        }

        String normalizedContent = normalizeText(content);
        String normalizedTitle = extractNormalizedTitle(content);
        int documentLength = Math.max(normalizedContent.length(), 1);
        double k1 = getK1();
        double b = getB();
        double avgDocumentLength = getAvgDocumentLength(documentIds, knowledgeBaseId);
        long totalChunkCount = Math.max(bm25StatisticsService.getTotalChunkCount(documentIds, knowledgeBaseId), 1);
        double score = 0.0;

        for (String term : terms) {
            String normalizedTerm = normalizeText(term);
            int termFrequency = countTermFrequency(normalizedContent, normalizedTerm);
            if (termFrequency <= 0) {
                continue;
            }

            long documentFrequency = bm25StatisticsService.getDocumentFrequency(normalizedTerm, documentIds, knowledgeBaseId);
            double idf = calculateIdf(totalChunkCount, documentFrequency);
            double numerator = termFrequency * (k1 + 1);
            double denominator = termFrequency + k1 * (1 - b + b * documentLength / avgDocumentLength);
            score += idf * numerator / denominator;
        }

        return score
                + calculatePhraseBoost(question, normalizedContent)
                + calculateTitleBoost(terms, normalizedTitle);
    }

    private int countTermFrequency(String content, String term) {
        if (content == null || term == null || term.isEmpty()) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = content.indexOf(term, index)) >= 0) {
            count++;
            index += term.length();
        }

        return count;
    }

    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("\\s+", "")
                .trim();
    }

    private String normalizeQueryPhrase(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "";
        }

        String phrase = normalizeText(question);
        for (String noiseWord : QUERY_NOISE_WORDS) {
            phrase = phrase.replace(noiseWord, "");
        }

        return phrase.trim();
    }

    private double calculatePhraseBoost(String question, String normalizedContent) {
        String phrase = normalizeQueryPhrase(question);
        if (phrase.length() < 2 || !normalizedContent.contains(phrase)) {
            return 0.0;
        }

        return getPhraseBoost();
    }

    private double calculateTitleBoost(List<String> terms, String normalizedTitle) {
        if (terms == null || terms.isEmpty() || normalizedTitle == null || normalizedTitle.isEmpty()) {
            return 0.0;
        }

        int hitCount = 0;
        for (String term : terms) {
            String normalizedTerm = normalizeText(term);
            if (!normalizedTerm.isEmpty() && normalizedTitle.contains(normalizedTerm)) {
                hitCount++;
            }
        }

        if (hitCount == 0) {
            return 0.0;
        }

        return getTitleBoost() * hitCount / terms.size();
    }

    private String extractNormalizedTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        String[] lines = content.split("\\R");
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                return normalizeText(line);
            }
        }

        return "";
    }

    private double calculateIdf(long totalChunkCount, long documentFrequency) {
        long safeDocumentFrequency = Math.max(documentFrequency, 1);
        return Math.log(1 + (totalChunkCount - safeDocumentFrequency + 0.5) / (safeDocumentFrequency + 0.5));
    }

    private double getK1() {
        if (bm25Properties.getK1() <= 0.0) {
            return 1.5;
        }

        return bm25Properties.getK1();
    }

    private double getB() {
        if (bm25Properties.getB() < 0.0 || bm25Properties.getB() > 1.0) {
            return 0.75;
        }

        return bm25Properties.getB();
    }

    private double getAvgDocumentLength(List<Long> documentIds, Long knowledgeBaseId) {
        double averageDocumentLength = bm25StatisticsService.getAverageDocumentLength(documentIds, knowledgeBaseId);
        if (averageDocumentLength > 0.0) {
            return averageDocumentLength;
        }

        if (bm25Properties.getAvgDocumentLength() <= 0.0) {
            return 500.0;
        }

        return bm25Properties.getAvgDocumentLength();
    }

    private double getPhraseBoost() {
        if (bm25Properties.getPhraseBoost() < 0.0) {
            return 0.5;
        }

        return bm25Properties.getPhraseBoost();
    }

    private double getTitleBoost() {
        if (bm25Properties.getTitleBoost() < 0.0) {
            return 0.3;
        }

        return bm25Properties.getTitleBoost();
    }
}
