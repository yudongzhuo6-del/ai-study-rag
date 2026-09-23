package com.yudong.aistudy.rag.vector;

import java.util.ArrayList;
import java.util.List;

public class VectorUtils {

    private VectorUtils() {
    }

    public static double[] parseVector(String vectorText) {
        if (vectorText == null || vectorText.trim().isEmpty()) {
            return new double[0];
        }

        String[] parts = vectorText.split(",");
        List<Double> values = new ArrayList<>();

        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }

            values.add(Double.parseDouble(part.trim()));
        }

        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }

        return vector;
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
