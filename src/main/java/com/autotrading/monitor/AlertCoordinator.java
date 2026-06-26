package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.model.MAEvent;
import com.autotrading.notification.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates alert dispatch with cooldown/throttle to prevent alert spam.
 * Each event type per stock has a configurable cooldown window (default 15 min).
 * <p>
 * The cooldown is recorded BEFORE the notification attempt, so a failed email
 * will not cause repeated alerts within the cooldown window.
 */
@Component
public class AlertCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AlertCoordinator.class);
    private static final int MAX_RECENT_ALERTS = 50;

    private final EmailNotificationService emailService;
    private final long cooldownMs;

    /** Cache: dedupKey -> last alert timestamp. */
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    /** Ring buffer of recently fired alerts (for dashboard display). */
    private final List<RecentAlert> recentAlerts = Collections.synchronizedList(new ArrayList<>());

    public AlertCoordinator(EmailNotificationService emailService, FutuProperties properties) {
        this.emailService = emailService;
        this.cooldownMs = properties.getMonitor().getAlertCooldownMinutes() * 60_000L;
    }

    /**
     * Handles an MA crossover event.
     * Checks cooldown, then dispatches to notification.
     */
    public void onMAEvent(MAEvent event) {
        if (!shouldAlert(event.dedupKey(), event.getTimestamp())) {
            return;
        }
        log.info("Dispatching MA event alert: {} MA{} {}", event.getStockKey(),
                event.getMaPeriod(), event.getDirection().getLabel());
        emailService.sendMAEventAlert(event);
        recordAlert("MA", event.getStockKey(), event.getStockName(),
                event.getDirection().getLabel() + " MA" + event.getMaPeriod(),
                event.getPrice(), event.getSession().getLabel(), event.getTimestamp());
    }

    /**
     * Determines if an alert should fire based on cooldown.
     * Records the timestamp before dispatch to prevent duplicates even if notification fails.
     */
    private boolean shouldAlert(String dedupKey, long timestamp) {
        Long lastTime = lastAlertTime.get(dedupKey);
        if (lastTime != null && (timestamp - lastTime) < cooldownMs) {
            log.debug("Alert suppressed (cooldown): {} (last={}ms ago)",
                    dedupKey, timestamp - lastTime);
            return false;
        }
        // Record BEFORE dispatch so failed emails don't cause repeats
        lastAlertTime.put(dedupKey, timestamp);
        return true;
    }

    /**
     * Records a fired alert in the ring buffer for dashboard display.
     */
    private void recordAlert(String type, String stockKey, String stockName,
                             String detail, double price, String session, long timestamp) {
        synchronized (recentAlerts) {
            recentAlerts.add(0, new RecentAlert(type, stockKey, stockName, detail, price, session, timestamp));
            while (recentAlerts.size() > MAX_RECENT_ALERTS) {
                recentAlerts.remove(recentAlerts.size() - 1);
            }
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
        lastAlertTime.clear();
        recentAlerts.clear();
    }

    /** DTO for dashboard display. */
    public record RecentAlert(String type, String stockKey, String stockName,
                              String detail, double price, String session, long timestamp) {}
}
