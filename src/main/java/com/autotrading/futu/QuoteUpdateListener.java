package com.autotrading.futu;

/**
 * Functional callback for real-time basic quote pushes.
 */
@FunctionalInterface
public interface QuoteUpdateListener {
    /**
     * Called when a real-time quote update is received.
     *
     * @param stockKey  unique stock key (market.code)
     * @param stockName stock display name
     * @param curPrice  current/latest price
     * @param preClose  previous close price
     */
    void onQuoteUpdate(String stockKey, String stockName, double curPrice, double preClose);
}
