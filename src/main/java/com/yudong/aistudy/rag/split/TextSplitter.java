package com.yudong.aistudy.rag.split;

import java.util.ArrayList;
import java.util.List;

public class TextSplitter {

    private static final String SENTENCE_ENDINGS = "\u3002\uff01\uff1f.!?\n\r";

    public static List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String normalizedText = normalizeText(text);
        List<String> topicSections = splitByTopic(normalizedText);

        for (String section : topicSections) {
            List<String> sectionChunks = splitByLength(section, chunkSize, overlap);
            chunks.addAll(applyTitleInheritance(section, sectionChunks));
        }

        return chunks;
    }

    public static List<String> splitByLength(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        int effectiveChunkSize = Math.max(1, chunkSize);//防止参数错误配置
        int effectiveOverlap = Math.max(0, Math.min(overlap, effectiveChunkSize - 1));
        int start = 0;

        while (start < text.length()) {
            int targetEnd = Math.min(start + effectiveChunkSize, text.length());
            int end = findBestEnd(text, start, targetEnd, effectiveChunkSize);

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            start = findOverlapStart(text, start, end, effectiveOverlap);

            if (start >= end) {
                start = end;
            }
        }

        return chunks;
    }

    public static List<String> splitByTopic(String text) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] paragraphs = text.split("\\n\\n");

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (isTopicBoundary(trimmed) && current.length() > 0) {
                sections.add(current.toString().trim());
                current.setLength(0);
            }

            current.append(trimmed).append("\n\n");
        }

        if (current.length() > 0) {
            sections.add(current.toString().trim());
        }

        return sections;
    }

    public static String extractSectionTitle(String section) {
        if (section == null || section.trim().isEmpty()) {
            return "";
        }

        String firstLine = getFirstLine(section).trim();
        if (isTopicBoundary(firstLine)) {
            return firstLine;
        }

        return "";
    }

    public static List<String> applyTitleInheritance(String section, List<String> sectionChunks) {
        List<String> chunks = new ArrayList<>();
        String title = extractSectionTitle(section);

        for (String chunk : sectionChunks) {
            if (title.isEmpty() || hasTitlePrefix(chunk, title)) {
                chunks.add(chunk);
            } else {
                chunks.add(title + "\n\n" + chunk);
            }
        }

        return chunks;
    }

    private static boolean hasTitlePrefix(String chunk, String title) {
        if (chunk == null || title == null) {
            return false;
        }
        String trimmedChunk = chunk.trim();
        return trimmedChunk.equals(title)
                || trimmedChunk.startsWith(title + "\n")
                || trimmedChunk.startsWith(title + "\r");
    }

    public static boolean isTopicBoundary(String paragraph) {
        String firstLine = getFirstLine(paragraph).trim();

        return isMarkdownHeading(firstLine)
                || isChineseChapterHeading(firstLine)
                || isNumberedHeading(firstLine)
                || isChineseNumberedHeading(firstLine);
    }

    private static String getFirstLine(String paragraph) {
        int index = paragraph.indexOf('\n');
        if (index < 0) {
            return paragraph;
        }

        return paragraph.substring(0, index);
    }

    private static boolean isMarkdownHeading(String line) {
        return line.matches("^#{1,6}\\s+.+");
    }

    private static boolean isChineseChapterHeading(String line) {
        return line.matches("^第[一二三四五六七八九十百千万0-9]{1,12}[章节篇部卷].*");
    }

    private static boolean isNumberedHeading(String line) {
        return line.matches("^\\d{1,3}([.．、]|\\))\\s*.+");
    }

    private static boolean isChineseNumberedHeading(String line) {
        return line.matches("^[一二三四五六七八九十百千万]{1,6}[、.．）)]\\s*.+")
                || line.matches("^（[一二三四五六七八九十百千万]{1,6}）\\s*.+");
    }

    public static String normalizeText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static int findBestEnd(String text, int start, int targetEnd, int chunkSize) {
        if (targetEnd >= text.length()) {
            return text.length();
        }

        int minEnd = start + Math.max(1, chunkSize) / 2;
        for (int i = targetEnd; i > minEnd; i--) {
            char c = text.charAt(i - 1);
            if (SENTENCE_ENDINGS.indexOf(c) >= 0) {
                return i;
            }
        }

        return targetEnd;
    }

    private static int findOverlapStart(String text, int chunkStart, int end, int overlap) {
        if (overlap <= 0) {
            return end;
        }

        int expectedStart = Math.max(chunkStart, end - overlap);
        int backwardLimit = Math.max(chunkStart, expectedStart - overlap);

        // Prefer the start of the sentence containing the expected overlap point.
        // This may make the overlap slightly larger, but avoids starting mid-sentence.
        for (int i = expectedStart; i > backwardLimit; i--) {
            if (isBoundaryCharacter(text.charAt(i - 1))) {
                return skipWhitespace(text, i, end);
            }
        }

        // If no earlier boundary is nearby, accept a slightly smaller overlap and
        // start after the next sentence boundary rather than cutting a sentence.
        for (int i = expectedStart; i < end; i++) {
            if (isBoundaryCharacter(text.charAt(i))) {
                return skipWhitespace(text, i + 1, end);
            }
        }

        // Unpunctuated text still needs a hard character-based fallback.
        return expectedStart;
    }

    private static boolean isBoundaryCharacter(char value) {
        return SENTENCE_ENDINGS.indexOf(value) >= 0;
    }

    private static int skipWhitespace(String text, int start, int end) {
        int result = start;
        while (result < end && Character.isWhitespace(text.charAt(result))) {
            result++;
        }
        return result;
    }
}
