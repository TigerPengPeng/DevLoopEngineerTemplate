package com.autotrading.indicator;

import com.autotrading.model.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when the current price crosses above (breakthrough) or below (breakdown)
 * an MA line. Maintains per-stock per-frequency per-MA state to detect transitions.
 */
@Component
public class CrossoverDetector {

    private static final Logger log = LoggerFactory.getLogger(CrossoverDetector.class);

    /**
     * Tracks whether the last price was above or below each MA.
     * Key format: stockKey + ":" + frequency + ":" + maPeriod
     */
    private final Map<String, Boolean> aboveState = new ConcurrentHashMap<>();

    /** Day-frequency overload: delegates to {@link #checkCrossover(String, String, int, double, double)}. */
    public Direction checkCrossover(String stockKey, int maPeriod, double price, double maValue) {
        return checkCrossover(stockKey, "day", maPeriod, price, maValue);
    }

    /**
     * Checks for a crossover event given the current price and MA value at a frequency.
     *
     * @param frequency "day" or "week" — state is tracked independently per frequency
     * @return BREAK_UP if price crossed from below to above MA,
     *         BREAK_DOWN if crossed from above to below,
     *         null if no crossover (first observation or no change)
     */
    public Direction checkCrossover(String stockKey, String frequency, int maPeriod, double price, double maValue) {
        if (Double.isNaN(maValue) || maValue <= 0) {
            return null;
        }

        String stateKey = stockKey + ":" + frequency + ":" + maPeriod;
        boolean currentlyAbove = price > maValue;
        Boolean previousAbove = aboveState.get(stateKey);

        if (previousAbove == null) {
            // First observation: record state but no event
            aboveState.put(stateKey, currentlyAbove);
            log.debug("Initial state for {} {} MA{}: {}", stockKey, frequency, maPeriod,
                    currentlyAbove ? "above" : "below");
            return null;
        }

        if (previousAbove != currentlyAbove) {
            aboveState.put(stateKey, currentlyAbove);
            Direction direction = currentlyAbove ? Direction.BREAK_UP : Direction.BREAK_DOWN;
            log.debug("Crossover detected: {} {} MA{} {} (price={}, MA={})",
                    stockKey, frequency, maPeriod, direction, price, maValue);
            return direction;
        }

        return null;
    }

    /**
     * Resets state for a stock (used after K-line refresh). Clears all frequencies.
     */
    public void resetState(String stockKey) {
        aboveState.keySet().removeIf(key -> key.startsWith(stockKey + ":"));
    }

    /** Resets state for a stock at a specific frequency ("day" or "week"). */
    public void resetState(String stockKey, String frequency) {
        aboveState.keySet().removeIf(key -> key.startsWith(stockKey + ":" + frequency + ":"));
    }

    /**
     * Resets all state (used on reconnect).
     */
    public void resetAll() {
        aboveState.clear();
    }

    /** Day-frequency overload: delegates to {@link #checkAll(String, String, double, Map, List)}. */
    public Map<Integer, Direction> checkAll(String stockKey, double price,
                                             Map<Integer, Double> maValues, List<Integer> periods) {
        return checkAll(stockKey, "day", price, maValues, periods);
    }

    /**
     * Checks all MA periods for a stock at a frequency.
     *
     * @param frequency "day" or "week"
     * @return map of MA period -> Direction (only contains entries with crossovers)
     */
    public Map<Integer, Direction> checkAll(String stockKey, String frequency, double price,
                                             Map<Integer, Double> maValues, List<Integer> periods) {
        Map<Integer, Direction> result = new java.util.HashMap<>();
        for (int period : periods) {
            Double maValue = maValues.get(period);
            if (maValue == null) {
                continue;
            }
            Direction direction = checkCrossover(stockKey, frequency, period, price, maValue);
            if (direction != null) {
                result.put(period, direction);
            }
        }
        return result;
    }

}
