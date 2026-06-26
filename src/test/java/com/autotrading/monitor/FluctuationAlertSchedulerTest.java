package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.market.MarketSessionService;
import com.autotrading.model.StockInfo;
import com.autotrading.model.TradingSession;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.startup.QuoteProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * NR-2: FluctuationAlertScheduler must deduplicate per stock via AlertNoiseFilter.
 * Within a cooldown window, the same stock should not appear in repeated batch emails.
 */
class FluctuationAlertSchedulerTest {

    private TimeWindowFluctuationMonitor monitor;
    private QuoteProcessor quoteProcessor;
    private MarketSessionService sessionService;
    private EmailNotificationService emailService;
    private AlertNoiseFilter noiseFilter;
    private FluctuationAlertScheduler scheduler;

    private final StockInfo stock = new StockInfo(StockInfo.MARKET_US, "AAPL", "Apple");

    @BeforeEach
    void setUp() {
        monitor = Mockito.mock(TimeWindowFluctuationMonitor.class);
        quoteProcessor = Mockito.mock(QuoteProcessor.class);
        sessionService = Mockito.mock(MarketSessionService.class);
        emailService = Mockito.mock(EmailNotificationService.class);
        noiseFilter = new AlertNoiseFilter(new FutuProperties());
        scheduler = new FluctuationAlertScheduler(monitor, quoteProcessor,
                sessionService, emailService, noiseFilter);

        when(quoteProcessor.isMonitoring()).thenReturn(true);
        when(quoteProcessor.getStocks()).thenReturn(List.of(stock));
        when(sessionService.getSession(anyInt())).thenReturn(TradingSession.REGULAR);

        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic("OR");
        when(monitor.getConfig()).thenReturn(cfg);
    }

    private TimeWindowFluctuationMonitor.StockFluctuationResult result() {
        return new TimeWindowFluctuationMonitor.StockFluctuationResult(
                stock.key(), stock.getName(), 105.0, "涨",
                List.of(), List.of(), System.currentTimeMillis());
    }

    @Test
    @DisplayName("NR-2: first qualifying stock sends batch email")
    void firstQualifyingSendsEmail() {
        when(monitor.evaluate(anyString(), anyString())).thenReturn(result());

        scheduler.evaluateAndAlert();

        verify(emailService, times(1)).sendFluctuationBatch(anyString(), anyString(),
                anyString(), anyList());
    }

    @Test
    @DisplayName("NR-2: same stock within cooldown does not send email")
    void sameStockWithinCooldownSuppressed() {
        when(monitor.evaluate(anyString(), anyString())).thenReturn(result());

        scheduler.evaluateAndAlert();  // first -> sends
        scheduler.evaluateAndAlert();  // second within cooldown -> suppressed

        verify(emailService, times(1)).sendFluctuationBatch(anyString(), anyString(),
                anyString(), anyList());
    }

    @Test
    @DisplayName("NR-2: no qualifying stocks means no email")
    void noQualifyingNoEmail() {
        when(monitor.evaluate(stock.key(), stock.getName())).thenReturn(null);

        scheduler.evaluateAndAlert();

        verify(emailService, never()).sendFluctuationBatch(anyString(), anyString(),
                anyString(), anyList());
    }

    @Test
    @DisplayName("NR-2: not monitoring means no scan")
    void notMonitoringNoScan() {
        when(quoteProcessor.isMonitoring()).thenReturn(false);

        scheduler.evaluateAndAlert();

        verify(monitor, never()).evaluate(anyString(), anyString());
    }
}
