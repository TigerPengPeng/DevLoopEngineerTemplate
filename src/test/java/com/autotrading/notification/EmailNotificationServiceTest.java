package com.autotrading.notification;

import com.autotrading.config.NotificationProperties;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * BF-1 backend contract: the email toggle / configured state controls whether
 * alert emails are dispatched. The dashboard button is clickable only when
 * isConfigured() is true; setEmailEnabled() flips the runtime on/off switch.
 */
class EmailNotificationServiceTest {

    private EmailNotificationService newService(NotificationProperties props) {
        EmailNotificationService svc = new EmailNotificationService(
                mock(JavaMailSender.class), props, mock(EmailHistoryService.class));
        svc.checkConfiguration();   // simulate @PostConstruct
        return svc;
    }

    private NotificationProperties props(boolean enabled, List<String> to) {
        NotificationProperties p = new NotificationProperties();
        p.setEnabled(enabled);
        p.setTo(to);
        p.setFrom("from@example.com");
        return p;
    }

    @Test
    @DisplayName("Disabled by config: not configured and email off")
    void disabledByConfig() {
        EmailNotificationService svc = newService(props(false, List.of("a@x.com")));
        assertFalse(svc.isConfigured());
        assertFalse(svc.isEmailEnabled());
    }

    @Test
    @DisplayName("No recipients: not configured even when enabled")
    void noRecipientsNotConfigured() {
        EmailNotificationService svc = newService(props(true, List.of()));
        assertFalse(svc.isConfigured());
        assertFalse(svc.isEmailEnabled());
    }

    @Test
    @DisplayName("Fully configured: configured=true, email enabled")
    void fullyConfiguredEnabled() {
        EmailNotificationService svc = newService(props(true, List.of("a@x.com", "b@x.com")));
        assertTrue(svc.isConfigured());
        assertTrue(svc.isEmailEnabled());
    }

    @Test
    @DisplayName("Runtime toggle off then on (BF-1)")
    void runtimeToggle() {
        EmailNotificationService svc = newService(props(true, List.of("a@x.com")));
        assertTrue(svc.isEmailEnabled());

        svc.setEmailEnabled(false);
        assertFalse(svc.isEmailEnabled(), "disabling the toggle must stop email dispatch");

        svc.setEmailEnabled(true);
        assertTrue(svc.isEmailEnabled());
    }

    @Test
    @DisplayName("When email disabled at runtime, alert send dispatches nothing (BF-1)")
    void sendSuppressedWhenDisabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailHistoryService history = mock(EmailHistoryService.class);
        EmailNotificationService svc = new EmailNotificationService(
                mailSender, props(true, List.of("a@x.com")), history);
        svc.checkConfiguration();
        svc.setEmailEnabled(false);

        svc.sendMAEventAlert(new MAEvent("11.AAPL", "Apple", 5, Direction.BREAK_UP,
                100.0, 99.0, TradingSession.REGULAR));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(history);
    }
}
