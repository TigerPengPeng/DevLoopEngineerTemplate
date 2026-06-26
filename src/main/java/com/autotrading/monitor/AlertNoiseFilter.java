package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized noise-reduction filter for ALL alert types.
 * <p>
 * When the same alert key (type + stock + detail) fires repeatedly within a
 * configurable cooldown window, only the first occurrence sends an email.
 * Subsequent occurrences are suppressed (return {@code false}) so callers can
 * still record them in the system without spamming the user's inbox.
 * <p>
 * The cooldown timestamp is recorded BEFORE returning {@code true}, so a failed
 * email send will not cause repeated alerts within the window.
 */
@Component
public class AlertNoiseFilter {

    private static final Logger log = LoggerFactory.getLogger(AlertNoiseFilter.class);

    private final long cooldownMs;

    /** Cache: dedupKey -> last email-sent timestamp. */
    private final Map<String, Long> lastEmailTime = new ConcurrentHashMap<>();

    public AlertNoiseFilter(FutuProperties properties) {
        this.cooldownMs = properties.getMonitor().getAlertCooldownMinutes() * 60_000L;
    }

    /**
     * Determines whether an email should be sent for this alert key.
     * Records the timestamp before returning true to prevent duplicates even
     * if the subsequent email send fails.
     *
     * @param alertType alert category (MA, FLUCTUATION, SIGNAL, BREAKDOWN)
     * @param dedupKey  unique key within the category (e.g. stockKey:detail)
     * @param timestamp event timestamp (epoch millis)
     * @return true if the email should be sent, false if suppressed by cooldown
     */
    public boolean shouldSendEmail(String alertType, String dedupKey, long timestamp) {
        String fullKey = alertType + ":" + dedupKey;
        Long lastTime = lastEmailTime.get(fullKey);
        if (lastTime != null && (timestamp - lastTime) < cooldownMs) {
            log.debug("Alert suppressed (noise filter): {} (last={}ms ago, cooldown={}ms)",
                    fullKey, timestamp - lastTime, cooldownMs);
            return false;
        }
        lastEmailTime.put(fullKey, timestamp);
        return true;
    }

    /**
     * Clears all cooldown state (used on reconnect / reset).
     */
    public void resetAll() {
        lastEmailTime.clear();
    }
}
