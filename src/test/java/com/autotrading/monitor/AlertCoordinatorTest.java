package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.repository.AlertRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertCoordinatorTest {

    private EmailNotificationService mockEmail;
    private AlertRecordRepository mockRepo;
    private AlertNoiseFilter noiseFilter;
    private AlertCoordinator coordinator;

    @BeforeEach
    void setUp() {
        mockEmail = Mockito.mock(EmailNotificationService.class);
        mockRepo = Mockito.mock(AlertRecordRepository.class);
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(0); // no cooldown for baseline test
        noiseFilter = new AlertNoiseFilter(props);
        coordinator = new AlertCoordinator(mockEmail, noiseFilter, mockRepo);
    }

    private AlertCoordinator coordinatorWithCooldown(int minutes) {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(minutes);
        AlertNoiseFilter nf = new AlertNoiseFilter(props);
        return new AlertCoordinator(mockEmail, nf, mockRepo);
    }

    @Test
    @DisplayName("MA event dispatches to email service")
    void testMAEventDispatch() {
        MAEvent event = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event);
        verify(mockEmail, times(1)).sendMAEventAlert(event);
    }

    @Test
    @DisplayName("Same MA event within cooldown is suppressed but still recorded")
    void testMAEventCooldown() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event1 = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event1);
        verify(mockEmail, times(1)).sendMAEventAlert(event1);

        // Same stock+period+direction within cooldown
        MAEvent event2 = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 151.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event2);
        verify(mockEmail, times(1)).sendMAEventAlert(any()); // still 1 total

        // Both alerts should be recorded (emailed + suppressed)
        verify(mockRepo, times(2)).save(any());
        var alerts = coordinator.getRecentAlerts();
        assertEquals(2, alerts.size());
        assertTrue(alerts.get(0).suppressed());  // most recent = suppressed
        assertFalse(alerts.get(1).suppressed()); // first = emailed
    }

    @Test
    @DisplayName("Different period for same stock is not suppressed")
    void testDifferentPeriodNotSuppressed() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent ma5 = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(ma5);
        verify(mockEmail, times(1)).sendMAEventAlert(ma5);

        MAEvent ma13 = new MAEvent("11.AAPL", "Apple", 13,
                Direction.BREAK_UP, 150.0, 140.0, TradingSession.REGULAR);
        coordinator.onMAEvent(ma13);
        verify(mockEmail, times(2)).sendMAEventAlert(any());
    }

    @Test
    @DisplayName("Opposite direction for same period is not suppressed")
    void testOppositeDirectionNotSuppressed() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent up = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(up);
        verify(mockEmail, times(1)).sendMAEventAlert(up);

        MAEvent down = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_DOWN, 144.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(down);
        verify(mockEmail, times(2)).sendMAEventAlert(any());
    }

    @Test
    @DisplayName("Reset clears cooldown state")
    void testResetCooldown() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event);
        verify(mockEmail, times(1)).sendMAEventAlert(event);

        coordinator.resetAll();

        // After reset, same event should fire again
        coordinator.onMAEvent(event);
        verify(mockEmail, times(2)).sendMAEventAlert(event);
    }

    @Test
    @DisplayName("Suppressed alerts are still persisted to database")
    void testSuppressedAlertPersisted() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event);

        // Same event again within cooldown -> suppressed but still recorded
        MAEvent dup = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 151.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(dup);

        // Two DB records: one non-suppressed, one suppressed
        verify(mockRepo, times(1)).save(Mockito.argThat(r -> !r.isSuppressed()));
        verify(mockRepo, times(1)).save(Mockito.argThat(r -> r.isSuppressed()));
    }
}
