package com.autotrading.model;

/**
 * Represents a trading session/market phase.
 * Maps from Futu QotMarketState enum values.
 */
public enum TradingSession {
    PRE_MARKET("盘前"),
    REGULAR("盘中"),
    AFTER_HOURS("盘后"),
    OVERNIGHT("夜盘"),
    CLOSED("休市");

    private final String label;

    TradingSession(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether alerts should fire during this session.
     */
    public boolean isTrading() {
        return this != CLOSED;
    }
}
