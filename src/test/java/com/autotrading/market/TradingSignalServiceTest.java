package com.autotrading.market;

import com.autotrading.model.StockInfo;
import com.autotrading.market.TradingSignalService.Signal;
import com.autotrading.market.TradingSignalService.SignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T16: TradingSignalService detects buy/sell points from volume-price relationships.
 * These tests pin the core contract: edge cases (empty / insufficient data) and one
 * representative SELL and BUY pattern, constructed so that only a single detector fires.
 */
class TradingSignalServiceTest {

    private KLineService kLineService;
    private TradingSignalService service;

    private static final int MARKET = StockInfo.MARKET_US;
    private static final String CODE = "AAPL";

    @BeforeEach
    void setUp() {
        kLineService = mock(KLineService.class);
        service = new TradingSignalService(kLineService);
    }

    private KLineService.KLineData bar(String time, double open, double high, double low,
                                       double close, long volume) {
        return new KLineService.KLineData(time, open, high, low, close, volume, 0.0);
    }

    /** n flat doji bars at `price` with constant `volume`. */
    private List<KLineService.KLineData> flat(int n, double price, long volume) {
        List<KLineService.KLineData> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(bar("d" + i, price, price, price, price, volume));
        }
        return list;
    }

    @Test
    @DisplayName("Empty K-line data returns no signals")
    void emptyDataReturnsNothing() {
        when(kLineService.getKLines(MARKET, CODE)).thenReturn(List.of());
        assertTrue(service.getSignals(MARKET, CODE).isEmpty());
    }

    @Test
    @DisplayName("Fewer than lookback+5 bars returns no signals")
    void insufficientBarsReturnsNothing() {
        when(kLineService.getKLines(MARKET, CODE)).thenReturn(flat(24, 100, 1000));
        assertTrue(service.getSignals(MARKET, CODE).isEmpty());
    }

    @Test
    @DisplayName("Heavy-volume drop (>2%) produces a single SELL signal (放量大跌)")
    void heavyVolumeDropIsSell() {
        List<KLineService.KLineData> klines = flat(24, 100, 1000);
        // last bar: opens 100, closes 97 (-3%), range 96-100, volume 3x the 20-day average
        klines.add(bar("d24", 100, 100, 96, 97, 3000));
        when(kLineService.getKLines(MARKET, CODE)).thenReturn(klines);

        List<Signal> signals = service.getSignals(MARKET, CODE);

        assertEquals(1, signals.size());
        Signal s = signals.get(0);
        assertEquals(SignalType.SELL, s.type());
        assertEquals(97.0, s.price());
        assertEquals("d24", s.date());
        assertTrue(s.reason().contains("放量大跌"), "reason should mention 放量大跌, got: " + s.reason());
    }

    @Test
    @DisplayName("Shrinking-volume decline produces a single BUY signal (缩量下跌)")
    void shrinkVolumeDeclineIsBuy() {
        // 22 flat bars at 100 / volume 1000, then 3 declining bars on shrinking volume
        List<KLineService.KLineData> klines = flat(22, 100, 1000);
        klines.add(bar("d22", 99, 99, 99, 99, 900));
        klines.add(bar("d23", 98, 98, 98, 98, 800));
        klines.add(bar("d24", 97, 97, 97, 97, 600));
        when(kLineService.getKLines(MARKET, CODE)).thenReturn(klines);

        List<Signal> signals = service.getSignals(MARKET, CODE);

        assertEquals(1, signals.size());
        Signal s = signals.get(0);
        assertEquals(SignalType.BUY, s.type());
        assertEquals(97.0, s.price());
        assertTrue(s.reason().contains("缩量下跌"), "reason should mention 缩量下跌, got: " + s.reason());
    }

    @Test
    @DisplayName("Flat market (no volume-price divergence) produces no signals")
    void flatMarketNoSignals() {
        when(kLineService.getKLines(MARKET, CODE)).thenReturn(flat(30, 100, 1000));
        assertTrue(service.getSignals(MARKET, CODE).isEmpty());
    }
}
