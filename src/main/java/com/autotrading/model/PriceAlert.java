package com.autotrading.model;

/**
 * Event fired when a stock's intraday price change exceeds the threshold.
 */
public class PriceAlert {

    private final String stockKey;
    private final String stockName;
    private final double price;
    private final double preClose;
    private final double changePercent;
    private final Direction direction;
    private final double threshold;
    private final TradingSession session;
    private final long timestamp;

    public PriceAlert(String stockKey, String stockName, double price, double preClose,
                      double changePercent, Direction direction, double threshold,
                      TradingSession session) {
        this.stockKey = stockKey;
        this.stockName = stockName;
        this.price = price;
        this.preClose = preClose;
        this.changePercent = changePercent;
        this.direction = direction;
        this.threshold = threshold;
        this.session = session;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStockKey() { return stockKey; }
    public String getStockName() { return stockName; }
    public double getPrice() { return price; }
    public double getPreClose() { return preClose; }
    public double getChangePercent() { return changePercent; }
    public Direction getDirection() { return direction; }
    public double getThreshold() { return threshold; }
    public TradingSession getSession() { return session; }
    public long getTimestamp() { return timestamp; }

    /**
     * Unique dedup key for cooldown: stock + direction.
     */
    public String dedupKey() {
        return "PRICE:" + stockKey + ":" + direction;
    }
}
