package com.autotrading.model;

/**
 * Event fired when a stock price crosses an MA line.
 */
public class MAEvent {

    private final String stockKey;
    private final String stockName;
    private final int maPeriod;
    private final Direction direction;
    private final double price;
    private final double maValue;
    private final TradingSession session;
    private final long timestamp;

    public MAEvent(String stockKey, String stockName, int maPeriod, Direction direction,
                   double price, double maValue, TradingSession session) {
        this.stockKey = stockKey;
        this.stockName = stockName;
        this.maPeriod = maPeriod;
        this.direction = direction;
        this.price = price;
        this.maValue = maValue;
        this.session = session;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStockKey() { return stockKey; }
    public String getStockName() { return stockName; }
    public int getMaPeriod() { return maPeriod; }
    public Direction getDirection() { return direction; }
    public double getPrice() { return price; }
    public double getMaValue() { return maValue; }
    public TradingSession getSession() { return session; }
    public long getTimestamp() { return timestamp; }

    /**
     * Unique dedup key for cooldown: stock + period + direction.
     */
    public String dedupKey() {
        return "MA:" + stockKey + ":MA" + maPeriod + ":" + direction;
    }
}
