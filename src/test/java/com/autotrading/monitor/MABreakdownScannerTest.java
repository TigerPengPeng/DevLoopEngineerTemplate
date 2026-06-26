package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.market.KLineService;
import com.autotrading.model.StockInfo;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.repository.AlertRecordRepository;
import com.autotrading.startup.QuoteProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NR-3: MABreakdownScanner must deduplicate per stock via AlertNoiseFilter.
 * Within a cooldown window, the same stock should not appear in repeated scans.
 */
class MABreakdownScannerTest {

    private KLineService kLineService;
    private QuoteProcessor quoteProcessor;
    private EmailNotificationService emailService;
    private AlertNoiseFilter noiseFilter;
    private MABreakdownScanner scanner;

    private final String stockKey = "11.AAPL";

    @BeforeEach
    void setUp() {
        kLineService = Mockito.mock(KLineService.class);
        quoteProcessor = Mockito.mock(QuoteProcessor.class);
        emailService = Mockito.mock(EmailNotificationService.class);
        FutuProperties props = new FutuProperties();
        noiseFilter = new AlertNoiseFilter(props);
        AlertRecordRepository alertRecordRepository = Mockito.mock(AlertRecordRepository.class);
        scanner = new MABreakdownScanner(kLineService, quoteProcessor,
                emailService, noiseFilter, alertRecordRepository, props);

        StockInfo stock = new StockInfo(11, "AAPL", "Apple");
        when(quoteProcessor.getStocks()).thenReturn(List.of(stock));
        when(quoteProcessor.getLatestPrices()).thenReturn(Map.of());
        // 30 closes trending down so current price (last close) is below MA5
        when(kLineService.getCloses(stockKey)).thenReturn(List.of(
                160.0, 155.0, 158.0, 150.0, 152.0,
                148.0, 145.0, 140.0, 138.0, 135.0,
                132.0, 130.0, 128.0, 125.0, 122.0,
                120.0, 118.0, 115.0, 112.0, 110.0,
                108.0, 106.0, 104.0, 102.0, 100.0,
                98.0, 96.0, 94.0, 92.0, 90.0));
    }

    @Test
    @DisplayName("NR-3: stock below MA triggers email on first scan")
    void firstScanTriggersEmail() {
        var items = scanner.runScan("test");
        assertFalse(items.isEmpty());

        verify(emailService, times(1)).sendMABreakdownReport(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("NR-3: same stock within cooldown does not email again")
    void sameStockWithinCooldownSuppressed() {
        scanner.runScan("test");   // first scan -> sends email
        scanner.runScan("test");   // second scan within cooldown -> suppressed

        verify(emailService, atMost(1)).sendMABreakdownReport(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("NR-3: no stocks below MA means no email")
    void noStocksBelowMANoEmail() {
        // 30 closes trending up so current price (last close) is above MA5
        when(kLineService.getCloses(stockKey)).thenReturn(List.of(
                90.0, 92.0, 94.0, 96.0, 98.0,
                100.0, 102.0, 104.0, 106.0, 108.0,
                110.0, 112.0, 115.0, 118.0, 120.0,
                122.0, 125.0, 128.0, 130.0, 132.0,
                135.0, 138.0, 140.0, 145.0, 148.0,
                150.0, 152.0, 155.0, 158.0, 160.0));

        var items = scanner.runScan("test");
        assertTrue(items.isEmpty());

        verify(emailService, never()).sendMABreakdownReport(anyString(), anyString(), anyList());
    }
}
