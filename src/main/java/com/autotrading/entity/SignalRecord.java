package com.autotrading.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted trading signal (buy/sell point) detected by the scanner.
 * Only populated when the scanner detects a new signal (post-deployment).
 */
@Entity
@Table(name = "signal_records")
public class SignalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockKey;

    private String stockName;

    @Column(nullable = false)
    private String signalType;

    private String strategy;

    @Column(length = 2000)
    private String reason;

    private double price;

    private String signalDate;

    @Column(nullable = false)
    private long timestamp;

    @Column(nullable = false)
    private Instant createdAt;

    public SignalRecord() {}

    public SignalRecord(String stockKey, String stockName, String signalType,
                         String strategy, String reason, double price,
                         String signalDate, long timestamp) {
        this.stockKey = stockKey;
        this.stockName = stockName;
        this.signalType = signalType;
        this.strategy = strategy;
        this.reason = reason;
        this.price = price;
        this.signalDate = signalDate;
        this.timestamp = timestamp;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getStockKey() { return stockKey; }
    public String getStockName() { return stockName; }
    public String getSignalType() { return signalType; }
    public String getStrategy() { return strategy; }
    public String getReason() { return reason; }
    public double getPrice() { return price; }
    public String getSignalDate() { return signalDate; }
    public long getTimestamp() { return timestamp; }
    public Instant getCreatedAt() { return createdAt; }
}
