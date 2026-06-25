package com.autotrading.indicator;

import com.autotrading.model.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossoverDetectorTest {

    private CrossoverDetector detector;
    private static final String STOCK = "11.AAPL";

    @BeforeEach
    void setUp() {
        detector = new CrossoverDetector();
    }

    @Test
    @DisplayName("First observation: no crossover event")
    void testFirstObservation() {
        Direction result = detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        assertNull(result);
    }

    @Test
    @DisplayName("Price crosses below MA: BREAK_DOWN")
    void testBreakDown() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 144.0, 145.0);
        assertEquals(Direction.BREAK_DOWN, result);
    }

    @Test
    @DisplayName("Price crosses above MA: BREAK_UP")
    void testBreakUp() {
        detector.checkCrossover(STOCK, 5, 140.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 146.0, 145.0);
        assertEquals(Direction.BREAK_UP, result);
    }

    @Test
    @DisplayName("No crossover when price stays on same side")
    void testNoCrossover() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 148.0, 145.0);
        assertNull(result);
    }

    @Test
    @DisplayName("Multiple periods tracked independently")
    void testMultiplePeriods() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        detector.checkCrossover(STOCK, 13, 140.0, 145.0);

        Map<Integer, Double> maValues = Map.of(5, 145.0, 13, 145.0);
        Map<Integer, Direction> result = detector.checkAll(STOCK, 140.0, maValues, List.of(5, 13));

        assertEquals(Direction.BREAK_DOWN, result.get(5));
        assertFalse(result.containsKey(13));
    }

    @Test
    @DisplayName("NaN MA value produces no event")
    void testNaNMA() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 140.0, Double.NaN);
        assertNull(result);
    }

    @Test
    @DisplayName("Reset clears state, next observation is initial")
    void testReset() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        detector.resetState(STOCK);
        Direction result = detector.checkCrossover(STOCK, 5, 140.0, 145.0);
        assertNull(result);
    }

    @Test
    @DisplayName("Exact equality does not trigger crossover")
    void testExactEqual() {
        detector.checkCrossover(STOCK, 5, 145.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 146.0, 145.0);
        assertEquals(Direction.BREAK_UP, result);
    }
}
