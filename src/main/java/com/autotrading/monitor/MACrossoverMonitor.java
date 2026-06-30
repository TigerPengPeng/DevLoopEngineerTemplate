package com.autotrading.monitor;

import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checks for MA crossover alert events when a real-time quote arrives.
 * <p>
 * Delegates to {@link MARuleEngine}, the configurable rule engine that replaced
 * the former hardcoded MA5/13/30/55 crossover logic. The {@code check} signature
 * is preserved so {@link com.autotrading.startup.QuoteProcessor} is unaffected.
 */
@Component
public class MACrossoverMonitor {

    private final MARuleEngine ruleEngine;

    public MACrossoverMonitor(MARuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * Evaluates configured MA alert rules for a stock.
     *
     * @param stockKey  unique stock key
     * @param stockName stock name
     * @param price     current price
     * @param session   current trading session
     * @return list of MA alert events (empty if none)
     */
    public List<MAEvent> check(String stockKey, String stockName, double price, TradingSession session) {
        return ruleEngine.check(stockKey, stockName, price, session);
    }
}
