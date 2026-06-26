package com.autotrading.monitor;

import com.autotrading.entity.AlertRecord;
import com.autotrading.model.MAEvent;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.repository.AlertRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates MA crossover alert dispatch with noise-reduction.
 * <p>
 * Every alert is recorded in the database and the in-memory ring buffer so the
 * user can always see what happened — even when the email was suppressed by the
 * noise filter. Emails are only sent when {@link AlertNoiseFilter} allows.
 */
@Component
public class AlertCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AlertCoordinator.class);
    private static final int MAX_RECENT_ALERTS = 50;

    private final EmailNotificationService emailService;
    private final AlertNoiseFilter noiseFilter;
    private final AlertRecordRepository alertRecordRepository;

    /** Ring buffer of recently fired alerts (for dashboard display). */
    private final List<RecentAlert> recentAlerts = Collections.synchronizedList(new ArrayList<>());

    public AlertCoordinator(EmailNotificationService emailService,
                            AlertNoiseFilter noiseFilter,
                            AlertRecordRepository alertRecordRepository) {
        this.emailService = emailService;
        this.noiseFilter = noiseFilter;
        this.alertRecordRepository = alertRecordRepository;
    }

    /**
     * Handles an MA crossover event.
     * Always records the alert; only sends email when the noise filter allows.
     */
    public void onMAEvent(MAEvent event) {
        String detail = event.getDirection().getLabel() + " MA" + event.getMaPeriod();
        boolean shouldEmail = noiseFilter.shouldSendEmail("MA", event.dedupKey(), event.getTimestamp());

        if (shouldEmail) {
            log.info("Dispatching MA event alert: {} MA{} {}", event.getStockKey(),
                    event.getMaPeriod(), event.getDirection().getLabel());
            emailService.sendMAEventAlert(event);
        } else {
            log.debug("MA alert suppressed (noise filter): {}", event.dedupKey());
        }

        recordAlert("MA", event.getStockKey(), event.getStockName(), detail,
                event.getPrice(), event.getSession().getLabel(), event.getTimestamp(), !shouldEmail);
    }

    /**
     * Records a fired alert in the ring buffer and database.
     * Called for every alert, whether emailed or suppressed.
     */
    private void recordAlert(String type, String stockKey, String stockName,
                             String detail, double price, String session,
                             long timestamp, boolean suppressed) {
        synchronized (recentAlerts) {
            recentAlerts.add(0, new RecentAlert(type, stockKey, stockName, detail,
                    price, session, timestamp, suppressed));
            while (recentAlerts.size() > MAX_RECENT_ALERTS) {
                recentAlerts.remove(recentAlerts.size() - 1);
            }
        }
        try {
            alertRecordRepository.save(new AlertRecord(
                    type, stockKey, stockName, detail, price, session, timestamp, suppressed));
        } catch (Exception e) {
            log.warn("Failed to persist alert record: {}", e.getMessage());
        }
    }

    /**
     * Returns a snapshot of recently fired alerts (newest first).
     */
    public List<RecentAlert> getRecentAlerts() {
        synchronized (recentAlerts) {
            return new ArrayList<>(recentAlerts);
        }
    }

    /**
     * Clears all cooldown state (used on reconnect).
     */
    public void resetAll() {
        noiseFilter.resetAll();
        recentAlerts.clear();
    }

    /** DTO for dashboard display. */
    public record RecentAlert(String type, String stockKey, String stockName,
                              String detail, double price, String session,
                              long timestamp, boolean suppressed) {}
}
