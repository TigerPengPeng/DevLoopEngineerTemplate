package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.PriceAlert;
import com.autotrading.model.TradingSession;
import com.autotrading.notification.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertCoordinatorTest {

    private EmailNotificationService mockEmail;
    private AlertCoordinator coordinator;

    @BeforeEach
    void setUp() {
        mockEmail = Mockito.mock(EmailNotificationService.class);
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(0); // no cooldown for baseline test
        coordinator = new AlertCoordinator(mockEmail, props);
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
    @DisplayName("Price alert dispatches to email service")
    void testPriceAlertDispatch() {
        PriceAlert alert = new PriceAlert("11.AAPL", "Apple", 155.0, 150.0,
                3.33, Direction.UP, 2.0, TradingSession.REGULAR);
        coordinator.onPriceAlert(alert);
        verify(mockEmail, times(1)).sendPriceAlert(alert);
    }

    @Test
    @DisplayName("Same MA event within cooldown is suppressed")
    void testMAEventCooldown() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        coordinator = new AlertCoordinator(mockEmail, props);

        MAEvent event1 = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event1);
        verify(mockEmail, times(1)).sendMAEventAlert(event1);

        // Same stock+period+direction within cooldown
        MAEvent event2 = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 151.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event2);
        verify(mockEmail, times(1)).sendMAEventAlert(any()); // still 1 total
    }

    @Test
    @DisplayName("Different period for same stock is not suppressed")
    void testDifferentPeriodNotSuppressed() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        coordinator = new AlertCoordinator(mockEmail, props);

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
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        coordinator = new AlertCoordinator(mockEmail, props);

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
    @DisplayName("Price alert for different stock is not suppressed")
    void testDifferentStockNotSuppressed() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        coordinator = new AlertCoordinator(mockEmail, props);

        PriceAlert aapl = new PriceAlert("11.AAPL", "Apple", 155.0, 150.0,
                3.33, Direction.UP, 2.0, TradingSession.REGULAR);
        coordinator.onPriceAlert(aapl);
        verify(mockEmail, times(1)).sendPriceAlert(aapl);

        PriceAlert googl = new PriceAlert("11.GOOGL", "Google", 145.0, 150.0,
                -3.33, Direction.DOWN, 2.0, TradingSession.REGULAR);
        coordinator.onPriceAlert(googl);
        verify(mockEmail, times(2)).sendPriceAlert(any());
    }

    @Test
    @DisplayName("Reset clears cooldown state")
    void testResetCooldown() {
        FutuProperties props = new FutuProperties();
        props.getMonitor().setAlertCooldownMinutes(15);
        coordinator = new AlertCoordinator(mockEmail, props);

        MAEvent event = new MAEvent("11.AAPL", "Apple", 5,
                Direction.BREAK_UP, 150.0, 145.0, TradingSession.REGULAR);
        coordinator.onMAEvent(event);
        verify(mockEmail, times(1)).sendMAEventAlert(event);

        coordinator.resetAll();

        // After reset, same event should fire again
        coordinator.onMAEvent(event);
        verify(mockEmail, times(2)).sendMAEventAlert(event);
    }
}
