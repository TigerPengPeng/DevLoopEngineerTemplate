package com.autotrading.monitor;

import com.autotrading.model.Direction;
import com.autotrading.model.PriceAlert;
import com.autotrading.model.TradingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Checks if intraday price change exceeds the configured threshold.
 * Maintains per-stock pre-close cache for change calculation.
 */
@Component
public class PriceFluctuationMonitor {

    private static final Logger log = LoggerFactory.getLogger(PriceFluctuationMonitor.class);

    private final double thresholdPercent;

    /** Cache: stockKey -> preClose price. */
    private final ConcurrentHashMap<String, Double> preCloseCache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PriceAlert> pendingAlerts = new CopyOnWriteArrayList<>();

    public PriceFluctuationMonitor(com.autotrading.config.FutuProperties properties) {
        this.thresholdPercent = properties.getMonitor().getPriceChangeThreshold();
    }

    /**
     * Checks price fluctuation for a stock.
     *
     * @param stockKey  unique stock key
     * @param stockName stock name
     * @param curPrice  current price
     * @param preClose  previous close price (0 if unknown)
     * @param session   current trading session
     * @return a PriceAlert if threshold exceeded, null otherwise
     */
    public PriceAlert check(String stockKey, String stockName, double curPrice,
                            double preClose, TradingSession session) {
        if (!session.isTrading()) {
            return null;
        }

        // Cache preClose from quote data (first valid value wins)
        if (preClose > 0) {
            preCloseCache.putIfAbsent(stockKey, preClose);
        }
        double basePreClose = preCloseCache.getOrDefault(stockKey, preClose);
        if (basePreClose <= 0) {
            return null;
        }

        double changePercent = (curPrice - basePreClose) / basePreClose * 100.0;

        if (Math.abs(changePercent) >= thresholdPercent) {
            Direction direction = changePercent > 0 ? Direction.UP : Direction.DOWN;
            PriceAlert alert = new PriceAlert(stockKey, stockName, curPrice, basePreClose,
                    changePercent, direction, thresholdPercent, session);
            log.info("Fluctuation alert: {} {} {}% (threshold=±{}%)",
                    stockKey, direction.getLabel(), String.format("%.2f", changePercent),
                    thresholdPercent);
            return alert;
        }

        return null;
    }

    /**
     * Resets pre-close cache for a stock (used after daily K-line refresh).
     */
    public void resetStock(String stockKey) {
        preCloseCache.remove(stockKey);
    }

    public void resetAll() {
        preCloseCache.clear();
    }
}
