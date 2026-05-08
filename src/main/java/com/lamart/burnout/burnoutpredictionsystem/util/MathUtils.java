package com.lamart.burnout.burnoutpredictionsystem.util;

import java.util.List;

public class MathUtils {
    public static double calculateMean(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static double calculateStandardDeviation(List<Double> values, double mean) {
        if (values == null || values.size() <= 1) return 0.0;
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    public static double calculateZScore(double value, double mean, double stdDev) {
        if (stdDev == 0.0) {
            if (value > mean) return 3.0;
            if (value < mean) return -3.0;
            return 0.0;
        }
        double z = (value - mean) / stdDev;
        return Math.max(-3.0, Math.min(3.0, z));
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