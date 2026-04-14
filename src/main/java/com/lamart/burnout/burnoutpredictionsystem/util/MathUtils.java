package com.lamart.burnout.burnoutpredictionsystem.util;

import java.util.List;

public class MathUtils {
    public static double calculateMean(List<Integer> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(v -> v).average().orElse(0.0);
    }

    public static double calculateStandardDeviation(List<Integer> values, double mean) {
        if (values == null || values.size() <= 1) return 0.0;

        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / (values.size() - 1);

        return Math.sqrt(variance);
    }

    public static double calculateZScore(int currentValue, double mean, double stdDev) {
        double effectiveStdDev = Math.max(stdDev, 1.0);
        double zScore = (currentValue - mean) / effectiveStdDev;

        return Math.max(-3.0, Math.min(3.0, zScore));
    }

    public static double sigmoid(double z) {
        if (z >= 0) {
            return 1.0 / (1.0 + Math.exp(-z));
        } else {
            double ez = Math.exp(z);
            return ez / (1.0 + ez);
        }
    }
}
