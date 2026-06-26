package com.autotrading.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted email notification record.
 * Only populated when the system actually sends (or fails to send) an email.
 */
@Entity
@Table(name = "email_records")
public class EmailRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;

    private String subject;

    private String recipient;

    private boolean success;

    @Column(length = 2000)
    private String error;

    @Column(nullable = false)
    private long timestamp;

    @Column(nullable = false)
    private Instant createdAt;

    public EmailRecordEntity() {}

    public EmailRecordEntity(String type, String subject, String recipient,
                              boolean success, String error, long timestamp) {
        this.type = type;
        this.subject = subject;
        this.recipient = recipient;
        this.success = success;
        this.error = error;
        this.timestamp = timestamp;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSubject() { return subject; }
    public String getRecipient() { return recipient; }
    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public long getTimestamp() { return timestamp; }
    public Instant getCreatedAt() { return createdAt; }
}
