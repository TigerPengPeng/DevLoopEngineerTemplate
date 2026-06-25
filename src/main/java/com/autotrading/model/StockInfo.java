package com.autotrading.model;

import java.util.Objects;

/**
 * Represents a stock with its market and code.
 */
public class StockInfo {

    public static final int MARKET_US = 11;
    public static final int MARKET_HK = 2;
    public static final int MARKET_CN_SH = 21;
    public static final int MARKET_CN_SZ = 22;

    private final int market;
    private final String code;
    private String name;

    public StockInfo(int market, String code, String name) {
        this.market = market;
        this.code = code;
        this.name = name;
    }

    public int getMarket() { return market; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /**
     * Unique key used for caching and map lookups.
     */
    public String key() {
        return market + "." + code;
    }

    public boolean isUSMarket() {
        return market == MARKET_US;
    }

    public boolean isChinaMarket() {
        return market == MARKET_CN_SH || market == MARKET_CN_SZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockInfo that)) return false;
        return market == that.market && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(market, code);
    }

    @Override
    public String toString() {
        return (name != null ? name + "(" + code + ")" : code) + "[M" + market + "]";
    }
}
