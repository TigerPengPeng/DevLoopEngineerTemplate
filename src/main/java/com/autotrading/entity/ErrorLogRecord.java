package com.autotrading.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted error log entry captured by the Logback appender.
 */
@Entity
@Table(name = "error_log_records")
public class ErrorLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String timestamp;

    private String logger;

    private String thread;

    @Column(length = 4000)
    private String message;

    @Column(length = 8000)
    private String stackTrace;

    @Column(nullable = false)
    private Instant createdAt;

    public ErrorLogRecord() {}

    public ErrorLogRecord(String timestamp, String logger, String thread,
                           String message, String stackTrace) {
        this.timestamp = timestamp;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
        this.stackTrace = stackTrace;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getLogger() { return logger; }
    public String getThread() { return thread; }
    public String getMessage() { return message; }
    public String getStackTrace() { return stackTrace; }
    public Instant getCreatedAt() { return createdAt; }
}
