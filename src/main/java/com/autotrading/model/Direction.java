package com.autotrading.model;

/**
 * Direction of a price movement or MA crossover event.
 */
public enum Direction {
    UP("涨"),
    DOWN("跌"),
    BREAK_UP("突破"),
    BREAK_DOWN("跌破");

    private final String label;

    Direction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
