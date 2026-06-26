package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.repository.AlertRecordRepository;
import com.autotrading.market.TradingSignalService;
import com.autotrading.model.StockInfo;
import com.autotrading.notification.EmailHistoryService;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.startup.QuoteProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BF-3: on restart the scanner must NOT email historical signals. Only signals
 * that appear after the first (seeding) scan are treated as real-time.
 */
class TradingSignalScannerTest {

    private TradingSignalService signalService;
    private QuoteProcessor quoteProcessor;
    private EmailNotificationService emailService;
    private TradingSignalScanner scanner;

    private final StockInfo stock = new StockInfo(StockInfo.MARKET_US, "AAPL", "Apple");

    @BeforeEach
    void setUp() {
        signalService = Mockito.mock(TradingSignalService.class);
        quoteProcessor = Mockito.mock(QuoteProcessor.class);
        emailService = Mockito.mock(EmailNotificationService.class);
        EmailHistoryService emailHistoryService = Mockito.mock(EmailHistoryService.class);
        AlertNoiseFilter noiseFilter = new AlertNoiseFilter(new FutuProperties());
        AlertRecordRepository alertRecordRepository = Mockito.mock(AlertRecordRepository.class);
        scanner = new TradingSignalScanner(signalService, quoteProcessor, emailService,
                emailHistoryService, noiseFilter, alertRecordRepository);

        when(quoteProcessor.isMonitoring()).thenReturn(true);
        when(quoteProcessor.getStocks()).thenReturn(List.of(stock));
    }

    private TradingSignalService.Signal signal(int idx, String date,
                                                TradingSignalService.SignalType type) {
        return new TradingSignalService.Signal(idx, date, type, 150.0, "MA均线交叉", "close vs MA13");
    }

    @Test
    @DisplayName("BF-3: first scan displays historical signal but does not email")
    void firstScanSeedsWithoutEmail() {
        when(signalService.getSignals(stock.getMarket(), stock.getCode()))
                .thenReturn(List.of(signal(0, "2026-06-20", TradingSignalService.SignalType.BUY)));

        scanner.scanSignals();

        // Historical signal is shown on the dashboard ...
        assertEquals(1, scanner.getRecords().size());
        // ... but never emailed.
        verify(emailService, never()).sendSignalAlert(anyString(), anyString());
    }

    @Test
    @DisplayName("BF-3: a brand-new signal on a later scan triggers email")
    void newSignalOnLaterScanTriggersEmail() {
        TradingSignalService.Signal historical = signal(0, "2026-06-20", TradingSignalService.SignalType.BUY);
        TradingSignalService.Signal fresh = signal(1, "2026-06-26", TradingSignalService.SignalType.SELL);

        // First scan: only the historical signal exists -> seeded, no email.
        when(signalService.getSignals(stock.getMarket(), stock.getCode()))
                .thenReturn(List.of(historical));
        scanner.scanSignals();
        verify(emailService, never()).sendSignalAlert(anyString(), anyString());

        // Second scan: a new signal appeared -> emailed exactly once.
        when(signalService.getSignals(stock.getMarket(), stock.getCode()))
                .thenReturn(List.of(historical, fresh));
        scanner.scanSignals();
        verify(emailService, times(1)).sendSignalAlert(anyString(), anyString());
    }

    @Test
    @DisplayName("BF-3: after resetAll the next scan re-seeds and still suppresses email")
    void resetReSeeds() {
        when(signalService.getSignals(stock.getMarket(), stock.getCode()))
                .thenReturn(List.of(signal(0, "2026-06-20", TradingSignalService.SignalType.BUY)));

        scanner.scanSignals();   // seeds
        scanner.resetAll();      // clears notifiedKeys + resets initialized
        scanner.scanSignals();   // re-seeds

        verify(emailService, never()).sendSignalAlert(anyString(), anyString());
    }
}
