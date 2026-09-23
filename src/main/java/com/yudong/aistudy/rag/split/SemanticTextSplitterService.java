package com.yudong.aistudy.rag.split;

import com.yudong.aistudy.config.properties.SplitterProperties;
import com.yudong.aistudy.rag.embedding.EmbeddingService;
import com.yudong.aistudy.rag.vector.VectorUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticTextSplitterService {

    private static final String SENTENCE_ENDINGS = "\u3002\uff01\uff1f.!?";

    private final EmbeddingService embeddingService;

    private final SplitterProperties splitterProperties;

    public SemanticTextSplitterService(EmbeddingService embeddingService,
                                       SplitterProperties splitterProperties) {
        this.embeddingService = embeddingService;
        this.splitterProperties = splitterProperties;
    }

    public List<String> split(String text) {
        if (!splitterProperties.isSemanticEnabled()) {
            return TextSplitter.split(text, splitterProperties.getChunkSize(), splitterProperties.getOverlap());
        }

        List<String> sections = splitBySemanticBoundary(text);
        List<String> chunks = new ArrayList<>();

        for (String section : sections) {
            List<String> sectionChunks = TextSplitter.splitByLength(
                    section,
                    splitterProperties.getChunkSize(),
                    splitterProperties.getOverlap()
            );
            chunks.addAll(TextSplitter.applyTitleInheritance(section, sectionChunks));
        }

        return chunks;
    }

    public List<String> splitBySemanticBoundary(String text) {
        List<String> sections = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return sections;
        }

        String normalizedText = TextSplitter.normalizeText(text);
        List<String> paragraphs = splitParagraphs(normalizedText);
        Map<String, double[]> embeddingCache = new HashMap<>();
        StringBuilder currentSection = new StringBuilder();
        String previousParagraph = null;
        boolean previousParagraphIsTitle = false;

        for (String paragraph : paragraphs) {
            if (previousParagraph == null) {
                currentSection.append(paragraph).append("\n\n");
                previousParagraph = paragraph;
                previousParagraphIsTitle = TextSplitter.isTopicBoundary(paragraph);
                continue;
            }

            boolean shouldStartNewSection = TextSplitter.isTopicBoundary(paragraph);
            // A heading owns the first body paragraph below it. Comparing a short heading
            // with that paragraph often produces a low similarity and used to create a
            // heading-only section, which then broke title inheritance.
            if (!shouldStartNewSection && !previousParagraphIsTitle) {
                double similarity = calculateSimilarity(previousParagraph, paragraph, embeddingCache);
                shouldStartNewSection = similarity < splitterProperties.getSemanticThreshold();
            }

            if (shouldStartNewSection && currentSection.length() > 0) {
                sections.add(currentSection.toString().trim());
                currentSection.setLength(0);
            }

            currentSection.append(paragraph).append("\n\n");
            previousParagraph = paragraph;
            previousParagraphIsTitle = TextSplitter.isTopicBoundary(paragraph);
        }

        if (currentSection.length() > 0) {
            sections.add(currentSection.toString().trim());//收尾
        }

        return sections;
    }


    private double calculateSimilarity(String paragraphA, String paragraphB, Map<String, double[]> embeddingCache) {
        if (paragraphA == null || paragraphA.trim().isEmpty()
                || paragraphB == null || paragraphB.trim().isEmpty()) {
            return 0.0;
        }

        double[] vectorA = getParagraphVector(paragraphA.trim(), embeddingCache);
        double[] vectorB = getParagraphVector(paragraphB.trim(), embeddingCache);

        return cosineSimilarity(vectorA, vectorB);
    }

    private double[] getParagraphVector(String paragraph, Map<String, double[]> embeddingCache) {
        return embeddingCache.computeIfAbsent(paragraph, text -> {
            String vectorText = embeddingService.embed(text);
            return VectorUtils.parseVector(vectorText);
        });
    }

    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<String> splitParagraphs(String text) {
        List<String> rawParagraphs = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return rawParagraphs;
        }

        StringBuilder current = new StringBuilder();
        String[] lines = text.split("\\n");

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                addParagraph(rawParagraphs, current);
                continue;
            }

            if (TextSplitter.isTopicBoundary(trimmed)) {
                addParagraph(rawParagraphs, current);
                rawParagraphs.add(trimmed);
                continue;
            }

            if (current.length() > 0) {
                current.append("\n");
            }

            current.append(trimmed);
        }

        addParagraph(rawParagraphs, current);

        List<String> sentenceAwareParagraphs = new ArrayList<>();
        for (String paragraph : rawParagraphs) {
            sentenceAwareParagraphs.addAll(splitLongParagraph(paragraph));
        }

        return mergeShortParagraphs(sentenceAwareParagraphs);
    }

    private void addParagraph(List<String> paragraphs, StringBuilder current) {
        String paragraph = current.toString().trim();
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph);
        }

        current.setLength(0);
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> paragraphs = new ArrayList<>();
        if (paragraph == null || paragraph.trim().isEmpty()) {
            return paragraphs;
        }

        String trimmed = paragraph.trim();
        if (TextSplitter.isTopicBoundary(trimmed) || trimmed.length() <= splitterProperties.getMaxParagraphLength()) {
            paragraphs.add(trimmed);
            return paragraphs;
        }

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            current.append(c);

            if (SENTENCE_ENDINGS.indexOf(c) >= 0
                    && current.length() >= splitterProperties.getMinParagraphLength()) {
                paragraphs.add(current.toString().trim());
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
        }

        return paragraphs;
    }

    private List<String> mergeShortParagraphs(List<String> paragraphs) {
        List<String> mergedParagraphs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (TextSplitter.isTopicBoundary(trimmed)) {
                flushShortParagraphBuffer(mergedParagraphs, buffer);
                mergedParagraphs.add(trimmed);
                continue;
            }

            if (buffer.length() > 0) {
                buffer.append("\n");
            }

            buffer.append(trimmed);

            if (buffer.length() >= splitterProperties.getMinParagraphLength()) {
                flushShortParagraphBuffer(mergedParagraphs, buffer);
            }
        }

        flushShortParagraphBuffer(mergedParagraphs, buffer);
        return mergedParagraphs;
    }

    private void flushShortParagraphBuffer(List<String> paragraphs, StringBuilder buffer) {
        String paragraph = buffer.toString().trim();
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph);
        }

        buffer.setLength(0);
    }
}
