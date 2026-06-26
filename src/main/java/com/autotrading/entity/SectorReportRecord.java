package com.autotrading.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted sector trend report.
 * The full report JSON is stored in a single column for simplicity,
 * since the report has deeply nested structure (sectors -> stocks).
 */
@Entity
@Table(name = "sector_report_records")
public class SectorReportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reportDate;

    @Column(nullable = false, length = 65535)
    private String reportJson;

    private String overallSentiment;

    private int sectorCount;

    @Column(nullable = false)
    private long generatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    public SectorReportRecord() {}

    public SectorReportRecord(String reportDate, String reportJson,
                               String overallSentiment, int sectorCount, long generatedAt) {
        this.reportDate = reportDate;
        this.reportJson = reportJson;
        this.overallSentiment = overallSentiment;
        this.sectorCount = sectorCount;
        this.generatedAt = generatedAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getReportDate() { return reportDate; }
    public String getReportJson() { return reportJson; }
    public String getOverallSentiment() { return overallSentiment; }
    public int getSectorCount() { return sectorCount; }
    public long getGeneratedAt() { return generatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
