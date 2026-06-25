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
 * an MA line. Maintains per-stock per-MA state to detect transitions.
 */
@Component
public class CrossoverDetector {

    private static final Logger log = LoggerFactory.getLogger(CrossoverDetector.class);

    /**
     * Tracks whether the last price was above or below each MA.
     * Key format: stockKey + ":" + maPeriod
     */
    private final Map<String, Boolean> aboveState = new ConcurrentHashMap<>();

    /**
     * Checks for a crossover event given the current price and MA value.
     *
     * @param stockKey  unique stock key
     * @param maPeriod  MA period
     * @param price     current price
     * @param maValue   current MA value (NaN if insufficient data)
     * @return BREAK_UP if price crossed from below to above MA,
     *         BREAK_DOWN if crossed from above to below,
     *         null if no crossover (first observation or no change)
     */
    public Direction checkCrossover(String stockKey, int maPeriod, double price, double maValue) {
        if (Double.isNaN(maValue) || maValue <= 0) {
            return null;
        }

        String stateKey = stockKey + ":" + maPeriod;
        boolean currentlyAbove = price > maValue;
        Boolean previousAbove = aboveState.get(stateKey);

        if (previousAbove == null) {
            // First observation: record state but no event
            aboveState.put(stateKey, currentlyAbove);
            log.debug("Initial state for {} MA{}: {}", stockKey, maPeriod,
                    currentlyAbove ? "above" : "below");
            return null;
        }

        if (previousAbove != currentlyAbove) {
            aboveState.put(stateKey, currentlyAbove);
            Direction direction = currentlyAbove ? Direction.BREAK_UP : Direction.BREAK_DOWN;
            log.debug("Crossover detected: {} MA{} {} (price={}, MA={})",
                    stockKey, maPeriod, direction, price, maValue);
            return direction;
        }

        return null;
    }

    /**
     * Resets state for a stock (used after K-line refresh).
     */
    public void resetState(String stockKey) {
        aboveState.keySet().removeIf(key -> key.startsWith(stockKey + ":"));
    }

    /**
     * Resets all state (used on reconnect).
     */
    public void resetAll() {
        aboveState.clear();
    }

    /**
     * Checks all MA periods for a stock.
     *
     * @param stockKey unique stock key
     * @param price    current price
     * @param maValues map of MA period -> MA value
     * @param periods  list of MA periods to check
     * @return map of MA period -> Direction (only contains entries with crossovers)
     */
    public Map<Integer, Direction> checkAll(String stockKey, double price,
                                             Map<Integer, Double> maValues, List<Integer> periods) {
        Map<Integer, Direction> result = new java.util.HashMap<>();
        for (int period : periods) {
            Double maValue = maValues.get(period);
            if (maValue == null) {
                continue;
            }
            Direction direction = checkCrossover(stockKey, period, price, maValue);
            if (direction != null) {
                result.put(period, direction);
            }
        }
        return result;
    }
}
