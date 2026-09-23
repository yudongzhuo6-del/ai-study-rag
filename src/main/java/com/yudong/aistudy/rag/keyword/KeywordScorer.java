package com.yudong.aistudy.rag.keyword;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KeywordScorer {//关键词分数计算逻辑

    private static final int MAX_SEARCH_KEYWORDS = 8;

    private static final List<String> QUESTION_STOP_WORDS = Arrays.asList(
            "什么", "怎么", "如何", "为什么", "是否", "请问", "一下", "一个", "这个", "那个",
            "的是", "是啥", "是什么", "有哪些", "多少", "以及", "和", "与", "的"
    );

    public List<String> splitKeywords(String question) {
        return splitKeywordsForScore(question);
    }

    public List<String> splitKeywordsForSearch(String question) {
        List<String> keywords = splitKeywordsForScore(question);
        if (keywords.size() <= MAX_SEARCH_KEYWORDS) {
            return keywords;
        }

        return keywords.subList(0, MAX_SEARCH_KEYWORDS);
    }

    public List<String> splitKeywordsForScore(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuestion = normalizeText(question);
        Set<String> keywords = new LinkedHashSet<>();

        for (String token : normalizedQuestion.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            String trimmed = token.trim();
            if (trimmed.length() <= 1 || isStopWord(trimmed)) {
                continue;
            }

            if (containsChinese(trimmed)) {
                keywords.addAll(generateChineseNgrams(trimmed, 2, 3));
            } else {
                keywords.add(trimmed);
            }
        }

        String phrase = normalizeQueryPhrase(question);
        if (!phrase.isEmpty()) {
            keywords.add(phrase);
        }

        if (keywords.isEmpty() && !normalizedQuestion.isEmpty()) {
            return Collections.singletonList(normalizedQuestion);
        }

        return new ArrayList<>(keywords);
    }

    public double score(String question, String content) {
        if (question == null || question.trim().isEmpty()) {
            return 0.0;
        }

        return score(splitKeywordsForScore(question), normalizeQueryPhrase(question), content);
    }

    public double score(List<String> keywords, String content) {
        return score(keywords, "", content);
    }

    private double score(List<String> keywords, String phrase, String content) {
        if (keywords == null || keywords.isEmpty() || content == null || content.trim().isEmpty()) {
            return 0.0;
        }

        String normalizedContent = normalizeText(content);
        String title = extractTitle(normalizedContent);
        double keywordHitScore = calculateHitScore(keywords, normalizedContent);
        double phraseScore = phrase != null && !phrase.isEmpty() && normalizedContent.contains(phrase) ? 1.0 : 0.0;
        double titleScore = calculateHitScore(keywords, title);
        double score = keywordHitScore * 0.6 + phraseScore * 0.25 + titleScore * 0.15;

        return Math.min(1.0, score);
    }

    private double calculateHitScore(List<String> keywords, String content) {
        if (keywords == null || keywords.isEmpty() || content == null || content.trim().isEmpty()) {
            return 0.0;
        }

        int hitCount = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                hitCount++;
            }
        }

        return hitCount * 1.0 / keywords.size();
    }

    private String normalizeQueryPhrase(String question) {
        String phrase = normalizeText(question);
        for (String stopWord : QUESTION_STOP_WORDS) {
            phrase = phrase.replace(stopWord, "");
        }

        return phrase.trim();
    }

    private String extractTitle(String content) {
        int index = content.indexOf('\n');
        if (index < 0) {
            return content;
        }

        return content.substring(0, index).trim();
    }

    private List<String> generateChineseNgrams(String text, int minLength, int maxLength) {
        List<String> ngrams = new ArrayList<>();
        if (text == null || text.length() < minLength) {
            return ngrams;
        }

        for (int length = minLength; length <= maxLength; length++) {
            if (text.length() < length) {
                continue;
            }

            for (int i = 0; i <= text.length() - length; i++) {
                String ngram = text.substring(i, i + length);
                if (!isStopWord(ngram)) {
                    ngrams.add(ngram);
                }
            }
        }

        return ngrams;
    }

    private boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }

        return false;
    }

    private boolean isStopWord(String text) {
        return QUESTION_STOP_WORDS.contains(text);
    }

    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("\\s+", "")
                .trim();
    }
}
