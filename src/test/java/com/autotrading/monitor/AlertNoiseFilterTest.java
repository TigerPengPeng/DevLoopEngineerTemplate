package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertNoiseFilterTest {

    private AlertNoiseFilter filter;

    @BeforeEach
    void setUp() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        filter = new AlertNoiseFilter(props);
    }

    @Test
    @DisplayName("First alert for a key always passes")
    void firstAlertPasses() {
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
    }

    @Test
    @DisplayName("Same key within cooldown is suppressed")
    void sameKeyWithinCooldownSuppressed() {
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
        assertFalse(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 2000L));
    }

    @Test
    @DisplayName("Same key after cooldown passes")
    void sameKeyAfterCooldownPasses() {
        long cooldown = 15 * 60_000L;
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L + cooldown + 1));
    }

    @Test
    @DisplayName("Different alert type for same stock is not suppressed")
    void differentAlertTypeNotSuppressed() {
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
        assertTrue(filter.shouldSendEmail("FLUCTUATION", "11.AAPL:涨", 2000L));
    }

    @Test
    @DisplayName("Different stock same type is not suppressed")
    void differentStockNotSuppressed() {
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
        assertTrue(filter.shouldSendEmail("MA", "11.TSLA:MA5:BREAK_UP", 2000L));
    }

    @Test
    @DisplayName("Zero cooldown allows all alerts through")
    void zeroCooldownAllowsAll() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(0);
        AlertNoiseFilter nf = new AlertNoiseFilter(props);

        assertTrue(nf.shouldSendEmail("MA", "key", 1000L));
        assertTrue(nf.shouldSendEmail("MA", "key", 1001L));
        assertTrue(nf.shouldSendEmail("MA", "key", 1002L));
    }

    @Test
    @DisplayName("Reset clears all cooldown state")
    void resetClearsAll() {
        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 1000L));
        assertFalse(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 2000L));

        filter.resetAll();

        assertTrue(filter.shouldSendEmail("MA", "11.AAPL:MA5:BREAK_UP", 2000L));
    }
}
