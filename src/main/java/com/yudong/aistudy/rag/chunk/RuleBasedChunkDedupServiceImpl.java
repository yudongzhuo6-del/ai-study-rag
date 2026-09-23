package com.yudong.aistudy.rag.chunk;

import com.yudong.aistudy.config.properties.ChunkDedupProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class RuleBasedChunkDedupServiceImpl implements ChunkDedupService {

    private final ChunkDedupProperties chunkDedupProperties;

    public RuleBasedChunkDedupServiceImpl(ChunkDedupProperties chunkDedupProperties) {
        this.chunkDedupProperties = chunkDedupProperties;
    }

    @Override
    public List<String> dedup(List<String> chunks) {
        if (!chunkDedupProperties.isEnabled() || chunks == null || chunks.isEmpty()) {
            return chunks;
        }

        List<String> dedupedChunks = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        Set<String> seenBodyHashes = new HashSet<>();
        List<Set<String>> retainedShingles = new ArrayList<>();

        for (String chunk : chunks) {
            if (chunk == null || chunk.trim().isEmpty()) {
                continue;
            }

            String normalizedChunk = normalizeChunk(chunk);
            if (normalizedChunk.length() < chunkDedupProperties.getMinLength()) {
                dedupedChunks.add(chunk);
                continue;
            }

            String hash = calculateHash(chunk);
            if (!seenHashes.add(hash)) {
                continue;
            }

            if (chunkDedupProperties.isBodyHashEnabled()) {
                String bodyHash = calculateBodyHash(chunk);
                if (!bodyHash.isEmpty() && !seenBodyHashes.add(bodyHash)) {
                    continue;
                }
            }

            if (chunkDedupProperties.isNearDuplicateEnabled()) {
                Set<String> shingles = buildShingles(normalizeBody(chunk));
                if (isNearDuplicate(shingles, retainedShingles)) {
                    continue;
                }
                retainedShingles.add(shingles);
            }

            dedupedChunks.add(chunk);
        }

        return dedupedChunks;
    }

    @Override
    public String calculateHash(String chunk) {
        if (chunk == null) {
            return "";
        }

        return sha256(normalizeChunk(chunk));
    }

    private String calculateBodyHash(String chunk) {
        String body = normalizeBody(chunk);
        if (body.length() < chunkDedupProperties.getMinLength()) {
            return "";
        }

        return sha256(body);
    }

    private String normalizeChunk(String chunk) {
        return chunk
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String normalizeBody(String chunk) {
        String normalizedChunk = normalizeChunk(chunk);
        if (normalizedChunk.isEmpty()) {
            return "";
        }

        String[] lines = normalizedChunk.split("\\n");
        StringBuilder body = new StringBuilder();
        boolean skippedLeadingTitle = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!skippedLeadingTitle && isTitleLine(trimmed)) {
                skippedLeadingTitle = true;
                continue;
            }

            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(trimmed);
        }

        return normalizeChunk(body.toString());
    }

    private boolean isTitleLine(String line) {
        return line.matches("^#{1,6}\\s+.+")
                || line.matches("^\u7b2c[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e070-9]{1,12}[\u7ae0\u8282\u7bc7\u90e8\u5377].*")
                || line.matches("^\\d{1,3}([.\\uff0e\\u3001]|\\))\\s*.+")
                || line.matches("^[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07]{1,6}[\u3001.\uff0e\uff09)]\\s*.+")
                || line.matches("^\uff08[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07]{1,6}\uff09\\s*.+");
    }

    private Set<String> buildShingles(String text) {
        Set<String> shingles = new HashSet<>();
        String compactText = text.replaceAll("\\s+", "");
        int shingleSize = Math.max(1, chunkDedupProperties.getShingleSize());
        if (compactText.length() < shingleSize) {
            if (!compactText.isEmpty()) {
                shingles.add(compactText);
            }
            return shingles;
        }

        for (int i = 0; i <= compactText.length() - shingleSize; i++) {
            shingles.add(compactText.substring(i, i + shingleSize));
        }

        return shingles;
    }

    private boolean isNearDuplicate(Set<String> currentShingles, List<Set<String>> retainedShingles) {
        if (currentShingles == null || currentShingles.isEmpty()) {
            return false;
        }

        for (Set<String> existingShingles : retainedShingles) {
            if (jaccardSimilarity(currentShingles, existingShingles)
                    >= chunkDedupProperties.getNearDuplicateThreshold()) {
                return true;
            }
        }

        return false;
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        int intersection = 0;
        for (String item : left) {
            if (right.contains(item)) {
                intersection++;
            }
        }

        int union = left.size() + right.size() - intersection;
        if (union == 0) {
            return 0.0;
        }

        return (double) intersection / union;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
