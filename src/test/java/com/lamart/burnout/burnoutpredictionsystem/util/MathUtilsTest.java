package com.lamart.burnout.burnoutpredictionsystem.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MathUtilsTest {

    @Test
    void testCalculateMean() {
        List<Integer> values = Arrays.asList(2, 4, 4, 4, 5, 5, 7, 9);
        double mean = MathUtils.calculateMean(values);
        assertEquals(5.0, mean, 0.001, "Среднее значение должно быть 5.0");
    }

    @Test
    void testCalculateMeanWithEmptyList() {
        assertEquals(0.0, MathUtils.calculateMean(Collections.emptyList()), "Пустой список должен возвращать 0.0");
        assertEquals(0.0, MathUtils.calculateMean(null), "Null должен возвращать 0.0");
    }

    @Test
    void testCalculateStandardDeviation() {
        List<Integer> values = Arrays.asList(2, 4, 4, 4, 5, 5, 7 ,9);
        double mean = 5.0;
        double stdDev = MathUtils.calculateStandardDeviation(values, mean);
        assertEquals(2.138, stdDev, 0.001, "Стандартное отклонение должно быть ~2.138");
    }

    @Test
    void testCalculateStandardDeviationEdgeCases() {
        List<Integer> singleValue = Collections.singletonList(5);
        assertEquals(0.0, MathUtils.calculateStandardDeviation(singleValue, 5.0), "Для одного элемента отклонение 0.0");
        assertEquals(0.0, MathUtils.calculateStandardDeviation(null, 5.0), "Для null списка отклонение 0.0");
    }

    @Test
    void testCalculateZScoreNormal() {
        double zScore = MathUtils.calculateZScore(7, 5.0, 2.0);
        assertEquals(1.0, zScore, 0.001, "Z-score (7-5)/2 должен быть 1.0");
    }

    @Test
    void testCalculateZScoreStdDev() {
        assertEquals(0.0, MathUtils.calculateZScore(5, 5.0, 0.0), "Если равно среднему, то 0.0");
        assertEquals(3.0, MathUtils.calculateZScore(7, 5.0, 0.0), "Если больше среднего при stdDev=0, то 3.0 (выброс)");
        assertEquals(-3.0, MathUtils.calculateZScore(2, 5.0, 0.0), "Если меньше среднего при stdDev=0, то -3.0 (выброс)");
    }

    @Test
    void testSigmoid() {
        assertEquals(0.5, MathUtils.sigmoid(0), 0.001, "Сигмоида от 0 должна быть 0.5");
        assertTrue(MathUtils.sigmoid(5) > 0.99, "Сигмоида от большего положительного числа стремиться к 1");
        assertTrue(MathUtils.sigmoid(-5) < 0.01, "Сигмоида от большего отрицательного числа стремится к 0");
    }
}
