package com.autotrading.indicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MACalculatorTest {

    @Test
    @DisplayName("MA5 of simple sequence [10,20,30,40,50] = 30")
    void testMA5_Simple() {
        List<Double> closes = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        double ma5 = MACalculator.calculateMA(closes, 5);
        assertEquals(30.0, ma5, 0.0001);
    }

    @Test
    @DisplayName("MA uses last N values, not all")
    void testMA_LastN() {
        List<Double> closes = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        double ma3 = MACalculator.calculateMA(closes, 3);
        // Last 3 values: 5, 6, 7 -> avg = 6
        assertEquals(6.0, ma3, 0.0001);
    }

    @Test
    @DisplayName("Insufficient data returns NaN")
    void testInsufficientData() {
        List<Double> closes = List.of(10.0, 20.0);
        double ma5 = MACalculator.calculateMA(closes, 5);
        assertTrue(Double.isNaN(ma5));
    }

    @Test
    @DisplayName("MA with empty list returns NaN")
    void testEmptyList() {
        double ma = MACalculator.calculateMA(List.of(), 5);
        assertTrue(Double.isNaN(ma));
    }

    @Test
    @DisplayName("MA13 with 15 values uses last 13")
    void testMA13() {
        List<Double> closes = List.of(
                1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0,
                9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0);
        double ma13 = MACalculator.calculateMA(closes, 13);
        // Last 13: 3..15 -> avg = (3+4+...+15)/13 = 117/13 = 9.0
        assertEquals(9.0, ma13, 0.0001);
    }

    @Test
    @DisplayName("calculateAll returns map for all periods")
    void testCalculateAll() {
        List<Double> closes = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0);
        Map<Integer, Double> result = MACalculator.calculateAll(closes, List.of(3, 5));

        assertEquals(50.0, result.get(3), 0.0001);
        assertEquals(40.0, result.get(5), 0.0001);
    }

    @Test
    @DisplayName("hasEnoughData returns correct boolean")
    void testHasEnoughData() {
        assertTrue(MACalculator.hasEnoughData(List.of(1.0, 2.0, 3.0), 3));
        assertFalse(MACalculator.hasEnoughData(List.of(1.0, 2.0), 3));
        assertFalse(MACalculator.hasEnoughData(null, 5));
    }

    @Test
    @DisplayName("MA30 and MA55 with sufficient data")
    void testMA30_MA55() {
        List<Double> closes = new java.util.ArrayList<>();
        for (int i = 1; i <= 55; i++) {
            closes.add((double) i);
        }
        // Last 30 values: 26..55 -> avg = (26+55)*30/2/30 = 40.5
        double ma30 = MACalculator.calculateMA(closes, 30);
        assertEquals(40.5, ma30, 0.001);

        // Last 55 values: 1..55 -> avg = 28.0
        double ma55 = MACalculator.calculateMA(closes, 55);
        assertEquals(28.0, ma55, 0.001);
    }
}
