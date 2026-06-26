package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized noise-reduction filter for ALL alert types (NR-1).
 * <p>
 * When the same alert key (type + stock + detail) fires repeatedly within a
 * configurable cooldown window, only the first occurrence sends an email.
 * Subsequent occurrences are suppressed (return {@code false}) so callers can
 * still record them in the system without spamming the user's inbox.
 * <p>
 * Each alert type can have its own cooldown window (NR-5 P1). When a per-type
 * override is not configured, the global {@code alertCooldownMinutes} is used.
 * <p>
 * The cooldown timestamp is recorded BEFORE returning {@code true}, so a failed
 * email send will not cause repeated alerts within the window.
 */
@Component
public class AlertNoiseFilter {

    private static final Logger log = LoggerFactory.getLogger(AlertNoiseFilter.class);

    private final long defaultCooldownMs;
    private final Map<String, Long> typeCooldownMs;

    /** Cache: dedupKey -> last email-sent timestamp. */
    private final Map<String, Long> lastEmailTime = new ConcurrentHashMap<>();

    public AlertNoiseFilter(FutuProperties properties) {
        FutuProperties.Monitor m = properties.getMonitor();
        this.defaultCooldownMs = m.getAlertCooldownMinutes() * 60_000L;

        // Per-type overrides (NR-5 P1): fall back to global default when null
        Map<String, Long> overrides = new java.util.HashMap<>();
        overrides.put("MA", resolveMs(m.getMaNoiseMinutes(), m));
        overrides.put("FLUCTUATION", resolveMs(m.getFluctuationNoiseMinutes(), m));
        overrides.put("SIGNAL", resolveMs(m.getSignalNoiseMinutes(), m));
        overrides.put("BREAKDOWN", resolveMs(m.getBreakdownNoiseMinutes(), m));
        this.typeCooldownMs = Map.copyOf(overrides);
    }

    private long resolveMs(Integer perType, FutuProperties.Monitor m) {
        if (perType != null) {
            return perType * 60_000L;
        }
        return m.getAlertCooldownMinutes() * 60_000L;
    }

    private long getCooldownMs(String alertType) {
        return typeCooldownMs.getOrDefault(alertType, defaultCooldownMs);
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
        long cooldown = getCooldownMs(alertType);
        if (lastTime != null && (timestamp - lastTime) < cooldown) {
            log.debug("Alert suppressed (noise filter): {} (last={}ms ago, cooldown={}ms)",
                    fullKey, timestamp - lastTime, cooldown);
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
