package com.autotrading.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted MA crossover alert event.
 * Only populated when the system actually triggers an alert (post-deployment).
 */
@Entity
@Table(name = "alert_records")
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String stockKey;

    private String stockName;

    private String detail;

    private double price;

    private String session;

    @Column(nullable = false)
    private long timestamp;

    /** True when the alert was noise-suppressed (no email sent). */
    @Column(nullable = false)
    private boolean suppressed;

    @Column(nullable = false)
    private Instant createdAt;

    public AlertRecord() {}

    public AlertRecord(String type, String stockKey, String stockName,
                       String detail, double price, String session, long timestamp) {
        this(type, stockKey, stockName, detail, price, session, timestamp, false);
    }

    public AlertRecord(String type, String stockKey, String stockName,
                       String detail, double price, String session, long timestamp, boolean suppressed) {
        this.type = type;
        this.stockKey = stockKey;
        this.stockName = stockName;
        this.detail = detail;
        this.price = price;
        this.session = session;
        this.timestamp = timestamp;
        this.suppressed = suppressed;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getStockKey() { return stockKey; }
    public String getStockName() { return stockName; }
    public String getDetail() { return detail; }
    public double getPrice() { return price; }
    public String getSession() { return session; }
    public long getTimestamp() { return timestamp; }
    public boolean isSuppressed() { return suppressed; }
    public Instant getCreatedAt() { return createdAt; }
}
