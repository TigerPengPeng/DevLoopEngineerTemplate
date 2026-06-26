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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

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

    private MAEvent event(String name, int period, Direction dir, double price, double maValue) {
        return new MAEvent("11." + name.toUpperCase(), name, period, dir, price, maValue, TradingSession.REGULAR);
    }

    @Test
    @DisplayName("MA event is buffered, not sent immediately")
    void testMAEventBuffered() {
        MAEvent event = event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0);
        coordinator.onMAEvent(event);

        // Buffered: no direct email, no batch yet
        verify(mockEmail, never()).sendMAEventAlert(any());
        verify(mockEmail, never()).sendMABatchAlert(any());

        coordinator.flushMABatch();

        ArgumentCaptor<List<MAEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockEmail, times(1)).sendMABatchAlert(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(event, captor.getValue().get(0));

        // Buffer drained: a second flush sends nothing
        coordinator.flushMABatch();
        verify(mockEmail, times(1)).sendMABatchAlert(any());
    }

    @Test
    @DisplayName("Flush with empty buffer sends no email")
    void testEmptyFlush() {
        coordinator.flushMABatch();
        verify(mockEmail, never()).sendMABatchAlert(any());
    }

    @Test
    @DisplayName("Same MA event within cooldown is suppressed (not buffered) but still recorded")
    void testMAEventCooldown() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event1 = event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0);
        coordinator.onMAEvent(event1);

        // Same stock+period+direction within cooldown
        MAEvent event2 = event("Apple", 5, Direction.BREAK_UP, 151.0, 145.0);
        coordinator.onMAEvent(event2);

        // Both alerts should be recorded (emailed/buffered + suppressed)
        verify(mockRepo, times(2)).save(any());
        var alerts = coordinator.getRecentAlerts();
        assertEquals(2, alerts.size());
        assertTrue(alerts.get(0).suppressed());  // most recent = suppressed
        assertFalse(alerts.get(1).suppressed()); // first = buffered

        // Flush sends only 1 (event2 was suppressed by cooldown)
        coordinator.flushMABatch();
        ArgumentCaptor<List<MAEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockEmail, times(1)).sendMABatchAlert(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    @DisplayName("Different period for same stock is not suppressed")
    void testDifferentPeriodNotSuppressed() {
        coordinator = coordinatorWithCooldown(15);

        coordinator.onMAEvent(event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0));
        coordinator.onMAEvent(event("Apple", 13, Direction.BREAK_UP, 150.0, 140.0));

        coordinator.flushMABatch();
        ArgumentCaptor<List<MAEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockEmail, times(1)).sendMABatchAlert(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    @DisplayName("Opposite direction for same period is not suppressed")
    void testOppositeDirectionNotSuppressed() {
        coordinator = coordinatorWithCooldown(15);

        coordinator.onMAEvent(event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0));
        coordinator.onMAEvent(event("Apple", 5, Direction.BREAK_DOWN, 144.0, 145.0));

        coordinator.flushMABatch();
        ArgumentCaptor<List<MAEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockEmail, times(1)).sendMABatchAlert(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    @DisplayName("Reset clears cooldown state so the event buffers again")
    void testResetCooldown() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event = event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0);
        coordinator.onMAEvent(event);

        coordinator.resetAll();

        // After reset, same event should buffer again
        coordinator.onMAEvent(event);

        coordinator.flushMABatch();
        ArgumentCaptor<List<MAEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockEmail, times(1)).sendMABatchAlert(captor.capture());
        // resetAll cleared the buffer; only the post-reset event remains,
        // proving cooldown was cleared (event buffered again, not suppressed)
        assertEquals(1, captor.getValue().size());
    }

    @Test
    @DisplayName("Reset clears the MA event buffer")
    void testResetClearsBuffer() {
        coordinator.onMAEvent(event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0));

        coordinator.resetAll();

        coordinator.flushMABatch();
        verify(mockEmail, never()).sendMABatchAlert(any());
    }

    @Test
    @DisplayName("Suppressed alerts are still persisted to database")
    void testSuppressedAlertPersisted() {
        coordinator = coordinatorWithCooldown(15);

        MAEvent event = event("Apple", 5, Direction.BREAK_UP, 150.0, 145.0);
        coordinator.onMAEvent(event);

        // Same event again within cooldown -> suppressed but still recorded
        MAEvent dup = event("Apple", 5, Direction.BREAK_UP, 151.0, 145.0);
        coordinator.onMAEvent(dup);

        // Two DB records: one non-suppressed, one suppressed
        verify(mockRepo, times(1)).save(Mockito.argThat(r -> !r.isSuppressed()));
        verify(mockRepo, times(1)).save(Mockito.argThat(r -> r.isSuppressed()));
    }
}
