package com.autotrading.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ring buffer tracking every email notification dispatched by the system.
 * Exposed via API for the dashboard "邮件通知" tab.
 */
@Service
public class EmailHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EmailHistoryService.class);
    private static final int MAX_RECORDS = 200;

    private final List<EmailRecord> records = Collections.synchronizedList(new ArrayList<>());

    /** Per-type counters for quick stats. */
    private final Map<String, Integer> typeCounters = new ConcurrentHashMap<>();

    public void record(String type, String subject, String recipient, boolean success, String error) {
        long now = System.currentTimeMillis();
        synchronized (records) {
            records.add(0, new EmailRecord(type, subject, recipient, success, error, now));
            while (records.size() > MAX_RECORDS) {
                records.remove(records.size() - 1);
            }
        }
        typeCounters.merge(type, 1, Integer::sum);
    }

    public List<EmailRecord> getRecords() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public Map<String, Integer> getTypeCounters() {
        return Map.copyOf(typeCounters);
    }

    public void clear() {
        synchronized (records) {
            records.clear();
        }
        typeCounters.clear();
    }

    /** Immutable DTO for API serialization. */
    public record EmailRecord(String type, String subject, String recipient,
                               boolean success, String error, long timestamp) {}
}
