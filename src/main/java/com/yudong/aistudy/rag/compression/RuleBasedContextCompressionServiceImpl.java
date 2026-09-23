package com.yudong.aistudy.rag.compression;

import com.yudong.aistudy.config.properties.ContextCompressionProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedContextCompressionServiceImpl implements ContextCompressionService {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？.!?\\n]+[。！？.!?]?");

    private static final String[] QUESTION_NOISE_WORDS = {
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
            "\u7684",
            "\u662f",
            "\u5417",
            "\u5462"
    };

    private final ContextCompressionProperties contextCompressionProperties;

    public RuleBasedContextCompressionServiceImpl(ContextCompressionProperties contextCompressionProperties) {
        this.contextCompressionProperties = contextCompressionProperties;
    }

    @Override
    public List<CompressedContext> compress(String question, List<RetrievedChunk> chunks) {
        List<CompressedContext> results = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return results;
        }

        if (!contextCompressionProperties.isEnabled()) {
            for (RetrievedChunk chunk : chunks) {
                results.add(buildCompressedContext(chunk, getChunkContent(chunk)));
            }
            return results;
        }

        List<String> keywords = splitKeywords(question);
        String phrase = normalizeQuestionPhrase(question);
        for (RetrievedChunk chunk : chunks) {
            String content = getChunkContent(chunk);
            String compressedContent = compressContent(content, keywords, phrase);
            results.add(buildCompressedContext(chunk, compressedContent));
        }

        return results;
    }

    private String compressContent(String content, List<String> keywords, String phrase) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        String normalizedContent = normalizeTextBlock(content);
        String title = extractTitle(normalizedContent);
        List<String> sentences = splitSentences(normalizedContent);
        List<ScoredSentence> scoredSentences = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (sentence.equals(title)) {
                continue;
            }

            double score = scoreSentence(sentence, keywords, phrase, i);
            if (score > 0.0) {
                scoredSentences.add(new ScoredSentence(sentence, i, score));
            }
        }

        if (scoredSentences.isEmpty()) {
            return buildFallbackContent(title, normalizedContent);
        }

        scoredSentences.sort(Comparator
                .comparing(ScoredSentence::getScore).reversed()
                .thenComparing(ScoredSentence::getIndex));

        int maxSentences = Math.max(1, contextCompressionProperties.getMaxSentencesPerChunk());
        List<ScoredSentence> selected = new ArrayList<>(scoredSentences.subList(0, Math.min(maxSentences, scoredSentences.size())));
        selected.sort(Comparator.comparing(ScoredSentence::getIndex));

        StringBuilder builder = new StringBuilder();
        if (!title.isEmpty()) {
            builder.append(title).append("\n\n");
        }

        for (ScoredSentence scoredSentence : selected) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(scoredSentence.getSentence().trim()).append('\n');
        }

        return limitLength(builder.toString().trim(), getMaxCharsPerChunk());
    }

    private double scoreSentence(String sentence, List<String> keywords, String phrase, int index) {
        String normalizedSentence = normalizeForMatch(sentence);
        if (normalizedSentence.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && normalizedSentence.contains(keyword)) {
                score += 1.0;
            }
        }

        if (!phrase.isEmpty() && normalizedSentence.contains(phrase)) {
            score += 2.0;
        }

        if (score > 0.0) {
            score += 1.0 / (index + 1);
        }

        return score;
    }

    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        if (sentences.isEmpty() && !content.trim().isEmpty()) {
            sentences.add(content.trim());
        }

        return sentences;
    }

    private List<String> splitKeywords(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> keywords = new LinkedHashSet<>();
        String normalizedQuestion = normalizeForMatch(question);
        String[] tokens = normalizedQuestion.split("[^a-z0-9\\u4e00-\\u9fa5]+");
        int minLength = Math.max(1, contextCompressionProperties.getMinKeywordLength());

        for (String token : tokens) {
            if (token == null || token.length() < minLength || isNoiseWord(token)) {
                continue;
            }

            if (containsChinese(token)) {
                keywords.addAll(generateChineseNgrams(token, minLength, 3));
            } else {
                keywords.add(token);
            }
        }

        String phrase = normalizeQuestionPhrase(question);
        if (!phrase.isEmpty()) {
            keywords.add(phrase);
        }

        return new ArrayList<>(keywords);
    }

    private List<String> generateChineseNgrams(String text, int minLength, int maxLength) {
        List<String> ngrams = new ArrayList<>();
        for (int length = minLength; length <= maxLength; length++) {
            if (text.length() < length) {
                continue;
            }

            for (int i = 0; i <= text.length() - length; i++) {
                String ngram = text.substring(i, i + length);
                if (!isNoiseWord(ngram)) {
                    ngrams.add(ngram);
                }
            }
        }

        return ngrams;
    }

    private String normalizeQuestionPhrase(String question) {
        String phrase = normalizeForMatch(question);
        for (String noiseWord : QUESTION_NOISE_WORDS) {
            phrase = phrase.replace(noiseWord, "");
        }

        return phrase.trim();
    }

    private String buildFallbackContent(String title, String content) {
        String fallback = limitLength(content, getFallbackChars());
        if (title.isEmpty() || fallback.contains(title)) {
            return fallback;
        }

        return limitLength(title + "\n\n" + fallback, getMaxCharsPerChunk());
    }

    private CompressedContext buildCompressedContext(RetrievedChunk retrievedChunk, String compressedContent) {
        CompressedContext compressedContext = new CompressedContext();
        compressedContext.setRetrievedChunk(retrievedChunk);
        compressedContext.setCompressedContent(compressedContent);

        String originalContent = getChunkContent(retrievedChunk);
        if (originalContent == null || originalContent.isEmpty()) {
            compressedContext.setCompressionRatio(0.0);
        } else {
            compressedContext.setCompressionRatio(compressedContent == null ? 0.0 : compressedContent.length() * 1.0 / originalContent.length());
        }

        return compressedContext;
    }

    private String getChunkContent(RetrievedChunk retrievedChunk) {
        if (retrievedChunk == null) {
            return "";
        }

        DocumentChunk chunk = retrievedChunk.getChunk();
        if (chunk == null || chunk.getContent() == null) {
            return "";
        }

        return chunk.getContent();
    }

    private String extractTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && isTitleLine(trimmed)) {
                return trimmed;
            }
        }

        return "";
    }

    private boolean isTitleLine(String line) {
        return line.matches("^#{1,6}\\s+.+")
                || line.matches("^\u7b2c[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e070-9]{1,12}[\u7ae0\u8282\u7bc7\u90e8\u5377].*")
                || line.matches("^\\d{1,3}([.\\uff0e\\u3001]|\\))\\s*.+")
                || line.matches("^[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07]{1,6}[\u3001.\\uff0e\\uff09)]\\s*.+")
                || line.matches("^\uff08[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07]{1,6}\uff09\\s*.+");
    }

    private boolean isNoiseWord(String text) {
        for (String noiseWord : QUESTION_NOISE_WORDS) {
            if (noiseWord.equals(text)) {
                return true;
            }
        }

        return false;
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

    private String normalizeTextBlock(String text) {
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase()
                .replaceAll("\\s+", "")
                .trim();
    }

    private String limitLength(String text, int maxChars) {
        if (text == null) {
            return "";
        }

        int limit = Math.max(1, maxChars);
        if (text.length() <= limit) {
            return text;
        }

        return text.substring(0, limit).trim();
    }

    private int getMaxCharsPerChunk() {
        return contextCompressionProperties.getMaxCharsPerChunk() <= 0
                ? 800
                : contextCompressionProperties.getMaxCharsPerChunk();
    }

    private int getFallbackChars() {
        return contextCompressionProperties.getFallbackChars() <= 0
                ? 200
                : contextCompressionProperties.getFallbackChars();
    }

    private static class ScoredSentence {

        private final String sentence;

        private final int index;

        private final double score;

        private ScoredSentence(String sentence, int index, double score) {
            this.sentence = sentence;
            this.index = index;
            this.score = score;
        }

        private String getSentence() {
            return sentence;
        }

        private int getIndex() {
            return index;
        }

        private double getScore() {
            return score;
        }
    }
}
