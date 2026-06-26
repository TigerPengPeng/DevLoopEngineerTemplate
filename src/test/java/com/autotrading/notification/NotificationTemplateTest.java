package com.autotrading.notification;

import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.PriceAlert;
import com.autotrading.model.TradingSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the String.format % collision bug that silently
 * killed all email notifications. The row()/colorRow() helpers produce
 * HTML containing literal '%' chars (e.g. width:30%). When these were
 * concatenated INTO a String.format() format string, the '%' was parsed
 * as a format specifier and threw UnknownFormatConversionException.
 */
class NotificationTemplateTest {

    private PriceAlert makePriceAlert() {
        return new PriceAlert(
                "11.MU", "Micron Technology", 125.50, 108.50,
                15.74, Direction.UP, 3.0, TradingSession.OVERNIGHT);
    }

    private MAEvent makeMAEvent() {
        return new MAEvent(
                "11.MU", "Micron Technology", 5, Direction.BREAK_UP,
                125.50, 122.00, TradingSession.OVERNIGHT);
    }

    @Test
    void priceAlertSubject_doesNotThrow() {
        String subject = NotificationTemplate.priceAlertSubject(makePriceAlert());
        assertNotNull(subject);
        assertTrue(subject.contains("MU"));
    }

    @Test
    void priceAlertBody_doesNotThrow() {
        String body = NotificationTemplate.priceAlertBody(makePriceAlert());
        assertNotNull(body);
        assertTrue(body.contains("Micron"));
        assertTrue(body.contains("width:30%"));
    }

    @Test
    void maEventSubject_doesNotThrow() {
        String subject = NotificationTemplate.maEventSubject(makeMAEvent());
        assertNotNull(subject);
        assertTrue(subject.contains("MA5"));
    }

    @Test
    void maEventBody_doesNotThrow() {
        String body = NotificationTemplate.maEventBody(makeMAEvent());
        assertNotNull(body);
        assertTrue(body.contains("Micron"));
        assertTrue(body.contains("width:30%"));
    }

    @Test
    void riskReportBody_doesNotThrow() {
        var items = java.util.List.of(
                new NotificationTemplate.RiskReportItem(
                        "11.MU", "Micron", 65, true, 15.74,
                        java.util.List.of("高位滞涨", "量价背离")));
        String body = NotificationTemplate.riskReportBody("美股", "2026-06-26", items);
        assertNotNull(body);
        assertTrue(body.contains("Micron"));
        assertTrue(body.contains("高风险"));
    }

    @Test
    void riskReportBody_emptyList_doesNotThrow() {
        String body = NotificationTemplate.riskReportBody("A股", "2026-06-26",
                java.util.List.of());
        assertNotNull(body);
        assertTrue(body.contains("无高风险"));
    }
}
