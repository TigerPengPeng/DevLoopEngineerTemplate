package com.autotrading.indicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates Simple Moving Average (SMA) values for multiple periods.
 */
public class MACalculator {

    private MACalculator() {}

    /**
     * Calculates the MA(N) value from the last N close prices.
     *
     * @param closes close prices in chronological order (oldest first)
     * @param period MA period (N)
     * @return the MA value, or NaN if insufficient data
     */
    public static double calculateMA(List<Double> closes, int period) {
        if (closes == null || closes.size() < period || period <= 0) {
            return Double.NaN;
        }
        double sum = 0;
        int start = closes.size() - period;
        for (int i = start; i < closes.size(); i++) {
            sum += closes.get(i);
        }
        return sum / period;
    }

    /**
     * Calculates MA values for all configured periods at once.
     *
     * @param closes close prices
     * @param periods MA periods (e.g., [5, 13, 30, 55])
     * @return map of period -> MA value (NaN if insufficient data)
     */
    public static Map<Integer, Double> calculateAll(List<Double> closes, List<Integer> periods) {
        Map<Integer, Double> result = new HashMap<>();
        for (int period : periods) {
            result.put(period, calculateMA(closes, period));
        }
        return result;
    }

    /**
     * Checks if there is enough data to calculate the given MA period.
     */
    public static boolean hasEnoughData(List<Double> closes, int period) {
        return closes != null && closes.size() >= period;
    }
}
