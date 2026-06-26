package com.autotrading.market;

import com.autotrading.market.RiskAssessmentService.RiskAssessment;
import com.autotrading.market.RiskAssessmentService.RiskLevel;
import com.autotrading.model.StockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T14: RiskAssessmentService combines multiple indicators into a single
 * risk score. These tests pin the deterministic parts of the scoring
 * (MA position factors and overall risk-level direction).
 */
class RiskAssessmentServiceTest {

    private KLineService kLineService;
    private RiskAssessmentService service;

    private final StockInfo stock = new StockInfo(StockInfo.MARKET_US, "AAPL", "Apple");

    @BeforeEach
    void setUp() {
        kLineService = mock(KLineService.class);
        service = new RiskAssessmentService(kLineService);
    }

    /** Builds K-line data with the given close sequence; volume constant, only the
     *  last bar carries the supplied change rate (used by the change-rate factor). */
    private List<KLineService.KLineData> klines(List<Double> closes, double lastChangeRate) {
        List<KLineService.KLineData> list = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            double c = closes.get(i);
            double cr = (i == closes.size() - 1) ? lastChangeRate : 0.0;
            list.add(new KLineService.KLineData("d" + i, c, c, c, c, 1000L, cr));
        }
        return list;
    }

    private List<Double> descending(int from, int count) {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < count; i++) closes.add((double) (from - i));
        return closes;
    }

    private List<Double> ascending(int from, int count) {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < count; i++) closes.add((double) (from + i));
        return closes;
    }

    /** Flat oscillating market: RSI ~= 50 (neutral), price above short MAs,
     *  no bearish alignment, MACD skipped (<35 bars) -> LOW with no risk factors. */
    private List<Double> oscillating(int count) {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < count; i++) closes.add(i % 2 == 0 ? 100.0 : 101.0);
        return closes;
    }

    @Test
    @DisplayName("Insufficient history (<30 bars) yields LOW with 数据不足 factor")
    void insufficientDataIsLow() {
        when(kLineService.getKLines(stock.getMarket(), stock.getCode()))
                .thenReturn(klines(ascending(100, 20), 0.0));

        RiskAssessment ra = service.assess(stock);

        assertEquals(RiskLevel.LOW, ra.level());
        assertEquals(0, ra.score());
        assertTrue(ra.riskFactors().contains("数据不足"));
    }

    @Test
    @DisplayName("Bearish setup (price below all MAs, downtrend) is HIGH risk")
    void bearishSetupIsHigh() {
        when(kLineService.getKLines(stock.getMarket(), stock.getCode()))
                .thenReturn(klines(descending(156, 56), -4.0));

        RiskAssessment ra = service.assess(stock);

        assertEquals(RiskLevel.HIGH, ra.level());
        assertTrue(ra.score() >= 60, "score should reach HIGH band, got " + ra.score());
        assertTrue(ra.riskFactors().stream().anyMatch(f -> f.contains("跌破MA5")));
        assertTrue(ra.riskFactors().stream().anyMatch(f -> f.contains("跌破MA13")));
        assertTrue(ra.riskFactors().stream().anyMatch(f -> f.contains("跌破MA30")));
    }

    @Test
    @DisplayName("Bullish setup (price above MAs, uptrend) records positive factors and is not HIGH")
    void bullishSetupNotHigh() {
        when(kLineService.getKLines(stock.getMarket(), stock.getCode()))
                .thenReturn(klines(ascending(100, 56), 1.0));

        RiskAssessment ra = service.assess(stock);

        assertNotEquals(RiskLevel.HIGH, ra.level(), "uptrend must not be HIGH risk");
        assertTrue(ra.positiveFactors().stream().anyMatch(f -> f.contains("站上MA5")));
        assertTrue(ra.riskFactors().stream().noneMatch(f -> f.contains("跌破MA")),
                "no bearish MA-break factors expected");
    }

    @Test
    @DisplayName("assessAll filters out LOW stocks with no risk factors")
    void assessAllFiltersLowNoRisk() {
        StockInfo bear = new StockInfo(StockInfo.MARKET_US, "BEAR", "BearCo");
        when(kLineService.getKLines(stock.getMarket(), stock.getCode()))
                .thenReturn(klines(oscillating(34), 1.0));
        when(kLineService.getKLines(bear.getMarket(), bear.getCode()))
                .thenReturn(klines(descending(156, 56), -4.0));

        List<RiskAssessment> results = service.assessAll(List.of(stock, bear));

        // Bullish stock (LOW, no risk factors) is filtered out; bearish retained.
        assertEquals(1, results.size());
        assertEquals("11.BEAR", results.get(0).stockKey());
    }
}
