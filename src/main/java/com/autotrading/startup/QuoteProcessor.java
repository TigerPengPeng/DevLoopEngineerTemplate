package com.autotrading.startup;

import com.autotrading.futu.QuoteUpdateListener;
import com.autotrading.market.MarketSessionService;
import com.autotrading.model.MAEvent;
import com.autotrading.model.StockInfo;
import com.autotrading.model.TradingSession;
import com.autotrading.monitor.AlertCoordinator;
import com.autotrading.monitor.MACrossoverMonitor;
import com.autotrading.monitor.TimeWindowFluctuationMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes real-time quote updates by dispatching to monitors and alert coordinator.
 * Implements QuoteUpdateListener and is wired onto FutuQuoteHandler during startup.
 *
 * Price fluctuation is handled by {@link TimeWindowFluctuationMonitor} in batch mode
 * (see FluctuationAlertScheduler). This processor only handles MA crossover events.
 */
@Component
public class QuoteProcessor implements QuoteUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(QuoteProcessor.class);

    private final MACrossoverMonitor maMonitor;
    private final AlertCoordinator alertCoordinator;
    private final MarketSessionService sessionService;
    private final TimeWindowFluctuationMonitor fluctuationMonitor;

    /** stockKey -> StockInfo (for display names and market info). */
    private final Map<String, StockInfo> stockRegistry = new ConcurrentHashMap<>();

    /** stockKey -> latest price snapshot (for dashboard display). */
    private final Map<String, PriceSnapshot> latestPrices = new ConcurrentHashMap<>();

    private volatile boolean monitoring = false;

    public QuoteProcessor(MACrossoverMonitor maMonitor,
                           AlertCoordinator alertCoordinator, MarketSessionService sessionService,
                           TimeWindowFluctuationMonitor fluctuationMonitor) {
        this.maMonitor = maMonitor;
        this.alertCoordinator = alertCoordinator;
        this.sessionService = sessionService;
        this.fluctuationMonitor = fluctuationMonitor;
    }

    public void registerStocks(List<StockInfo> stocks) {
        stockRegistry.clear();
        for (StockInfo stock : stocks) {
            stockRegistry.put(stock.key(), stock);
        }
    }

    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
        log.info("Quote monitoring {}", monitoring ? "enabled" : "disabled");
    }

    @Override
    public void onQuoteUpdate(String stockKey, String stockName, double curPrice, double preClose) {
        // Always cache the latest price (even before monitoring is fully enabled)
        latestPrices.put(stockKey, new PriceSnapshot(curPrice, preClose, System.currentTimeMillis()));

        // Record price tick for time-windowed fluctuation monitoring
        if (curPrice > 0) {
            fluctuationMonitor.recordPrice(stockKey, curPrice, System.currentTimeMillis());
        }

        if (!monitoring) {
            return;
        }

        StockInfo stock = stockRegistry.get(stockKey);
        int market = stock != null ? stock.getMarket() : parseMarket(stockKey);
        TradingSession session = sessionService.getSession(market);

        if (!session.isTrading()) {
            return;
        }

        // Check MA crossovers
        List<MAEvent> maEvents = maMonitor.check(stockKey, stockName, curPrice, session);
        for (MAEvent event : maEvents) {
            alertCoordinator.onMAEvent(event);
        }
    }

    private int parseMarket(String stockKey) {
        try {
            return Integer.parseInt(stockKey.split("\\.")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    public int getRegisteredCount() {
        return stockRegistry.size();
    }

    public List<StockInfo> getStocks() {
        return new ArrayList<>(stockRegistry.values());
    }

    public Map<String, PriceSnapshot> getLatestPrices() {
        return Map.copyOf(latestPrices);
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    /** Latest price + reference close for one stock. */
    public record PriceSnapshot(double curPrice, double preClose, long updateTime) {}
}
