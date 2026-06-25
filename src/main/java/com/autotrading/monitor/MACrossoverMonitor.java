package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.indicator.CrossoverDetector;
import com.autotrading.indicator.MACalculator;
import com.autotrading.market.KLineService;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks for MA crossover events when a real-time quote arrives.
 * Uses cached close prices from KLineService and CrossoverDetector for state tracking.
 */
@Component
public class MACrossoverMonitor {

    private static final Logger log = LoggerFactory.getLogger(MACrossoverMonitor.class);

    private final KLineService kLineService;
    private final CrossoverDetector crossoverDetector;
    private final List<Integer> maPeriods;

    public MACrossoverMonitor(KLineService kLineService, CrossoverDetector crossoverDetector,
                               FutuProperties properties) {
        this.kLineService = kLineService;
        this.crossoverDetector = crossoverDetector;
        this.maPeriods = properties.getMonitor().getMaPeriods();
    }

    /**
     * Checks all MA periods for crossover events.
     *
     * @param stockKey  unique stock key
     * @param stockName stock name
     * @param price     current price
     * @param session   current trading session
     * @return list of MA crossover events (empty if none)
     */
    public List<MAEvent> check(String stockKey, String stockName, double price, TradingSession session) {
        if (!session.isTrading()) {
            return List.of();
        }

        List<Double> closes = kLineService.getCloses(stockKey);
        if (closes.isEmpty()) {
            return List.of();
        }

        Map<Integer, Double> maValues = MACalculator.calculateAll(closes, maPeriods);
        Map<Integer, Direction> crossovers = crossoverDetector.checkAll(stockKey, price, maValues, maPeriods);

        List<MAEvent> events = new ArrayList<>();
        for (Map.Entry<Integer, Direction> entry : crossovers.entrySet()) {
            int period = entry.getKey();
            Direction direction = entry.getValue();
            double maValue = maValues.get(period);
            MAEvent event = new MAEvent(stockKey, stockName, period, direction, price, maValue, session);
            events.add(event);
            log.info("MA crossover: {} MA{} {} (price={}, MA={})",
                    stockKey, period, direction.getLabel(), price, maValue);
        }

        return events;
    }

    /**
     * Resets crossover state for a stock (used after K-line refresh).
     */
    public void resetStock(String stockKey) {
        crossoverDetector.resetState(stockKey);
    }

    public void resetAll() {
        crossoverDetector.resetAll();
    }
}
