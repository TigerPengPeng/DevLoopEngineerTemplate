package com.autotrading.indicator;

import com.autotrading.model.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossoverDetectorFreqTest {

    private CrossoverDetector detector;
    private static final String STOCK = "11.AAPL";

    @BeforeEach
    void setUp() {
        detector = new CrossoverDetector();
    }

    @Test
    @DisplayName("Day and week frequencies track state independently")
    void testIndependentFrequencyState() {
        // Day MA5=145: price starts below
        detector.checkCrossover(STOCK, "day", 5, 140.0, 145.0);
        // Week MA5=145: price starts above (independent state)
        detector.checkCrossover(STOCK, "week", 5, 150.0, 145.0);

        // Day: price rises above -> BREAK_UP
        Direction dayDir = detector.checkCrossover(STOCK, "day", 5, 146.0, 145.0);
        assertEquals(Direction.BREAK_UP, dayDir);

        // Week: price still above, no crossover
        Direction weekDir = detector.checkCrossover(STOCK, "week", 5, 148.0, 145.0);
        assertNull(weekDir);
    }

    @Test
    @DisplayName("Day-frequency overload delegates to day state")
    void testDayOverloadDelegates() {
        detector.checkCrossover(STOCK, 5, 150.0, 145.0);
        Direction result = detector.checkCrossover(STOCK, 5, 144.0, 145.0);
        assertEquals(Direction.BREAK_DOWN, result);
    }

    @Test
    @DisplayName("checkAll frequency overload evaluates per-frequency state")
    void testCheckAllFrequency() {
        detector.checkCrossover(STOCK, "day", 5, 140.0, 145.0);
        detector.checkCrossover(STOCK, "day", 13, 140.0, 145.0);

        Map<Integer, Direction> result = detector.checkAll(STOCK, "day", 146.0,
                Map.of(5, 145.0, 13, 145.0), List.of(5, 13));

        assertEquals(Direction.BREAK_UP, result.get(5));
        assertEquals(Direction.BREAK_UP, result.get(13));
    }

    @Test
    @DisplayName("resetState(frequency) only clears that frequency")
    void testResetStateByFrequency() {
        detector.checkCrossover(STOCK, "day", 5, 150.0, 145.0);
        detector.checkCrossover(STOCK, "week", 5, 150.0, 145.0);

        detector.resetState(STOCK, "day");
        // Day: next observation is initial (no event)
        assertNull(detector.checkCrossover(STOCK, "day", 5, 140.0, 145.0));
        // Week: still has state, should detect BREAK_DOWN
        assertEquals(Direction.BREAK_DOWN, detector.checkCrossover(STOCK, "week", 5, 140.0, 145.0));
    }
}
