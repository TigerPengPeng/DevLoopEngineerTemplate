package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.TradingSession;
import com.autotrading.model.StockInfo;
import com.futu.openapi.pb.QotCommon;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class MarketSessionServiceTest {

    private MarketSessionService service;

    @BeforeEach
    void setUp() {
        FutuConnectionManager mockConn = Mockito.mock(FutuConnectionManager.class);
        AsyncRequestBridge mockBridge = Mockito.mock(AsyncRequestBridge.class);
        service = new MarketSessionService(mockConn, mockBridge);
        // Pin the clock to a Saturday so the fallback time inference is
        // deterministic (all markets CLOSED on a weekend) instead of flaky.
        service.setClock(Clock.fixed(Instant.parse("2026-06-27T13:00:00Z"), ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("US pre-market maps to PRE_MARKET")
    void testPreMarket() {
        assertEquals(TradingSession.PRE_MARKET,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_PreMarketBegin.getNumber()));
        assertEquals(TradingSession.PRE_MARKET,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_PreMarketEnd.getNumber()));
    }

    @Test
    @DisplayName("Morning/Afternoon maps to REGULAR")
    void testRegular() {
        assertEquals(TradingSession.REGULAR,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_Morning.getNumber()));
        assertEquals(TradingSession.REGULAR,
                service.mapMarketState(21, QotCommon.QotMarketState.QotMarketState_Afternoon.getNumber()));
    }

    @Test
    @DisplayName("US after-hours maps to AFTER_HOURS")
    void testAfterHours() {
        assertEquals(TradingSession.AFTER_HOURS,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_AfterHoursBegin.getNumber()));
        assertEquals(TradingSession.AFTER_HOURS,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_AfterHoursEnd.getNumber()));
    }

    @Test
    @DisplayName("US overnight maps to OVERNIGHT")
    void testOvernight() {
        assertEquals(TradingSession.OVERNIGHT,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_NightOpen.getNumber()));
        assertEquals(TradingSession.OVERNIGHT,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_NightEnd.getNumber()));
    }

    @Test
    @DisplayName("Closed maps to CLOSED")
    void testClosed() {
        assertEquals(TradingSession.CLOSED,
                service.mapMarketState(11, QotCommon.QotMarketState.QotMarketState_Closed.getNumber()));
    }

    @Test
    @DisplayName("Unknown state value maps to CLOSED")
    void testUnknownState() {
        assertEquals(TradingSession.CLOSED, service.mapMarketState(11, 9999));
    }

    @Test
    @DisplayName("Auction maps to REGULAR")
    void testAuction() {
        assertEquals(TradingSession.REGULAR,
                service.mapMarketState(21, QotCommon.QotMarketState.QotMarketState_Auction.getNumber()));
    }

    @Test
    @DisplayName("Rest period maps to REGULAR")
    void testRest() {
        assertEquals(TradingSession.REGULAR,
                service.mapMarketState(21, QotCommon.QotMarketState.QotMarketState_Rest.getNumber()));
    }

    @Test
    @DisplayName("STIB after-hours maps to AFTER_HOURS")
    void testStibAfterHours() {
        assertEquals(TradingSession.AFTER_HOURS,
                service.mapMarketState(21, QotCommon.QotMarketState.QotMarketState_StibAfterHoursBegin.getNumber()));
    }

    @Test
    @DisplayName("Default session is CLOSED for unregistered market")
    void testDefaultSession() {
        assertEquals(TradingSession.CLOSED, service.getSession(99));
    }

    @Test
    @DisplayName("isTrading returns false for CLOSED")
    void testIsTradingClosed() {
        assertFalse(service.isTrading(11));
    }

    @Test
    @DisplayName("US regular hours inferred as REGULAR (10:00 ET)")
    void testInferUSRegular() {
        service.setClock(Clock.fixed(Instant.parse("2026-06-26T14:00:00Z"), ZoneId.systemDefault()));
        assertEquals(TradingSession.REGULAR, service.inferSession(StockInfo.MARKET_US));
    }

    @Test
    @DisplayName("US pre-market inferred as PRE_MARKET (04:00 ET)")
    void testInferUSPreMarket() {
        service.setClock(Clock.fixed(Instant.parse("2026-06-26T08:00:00Z"), ZoneId.systemDefault()));
        assertEquals(TradingSession.PRE_MARKET, service.inferSession(StockInfo.MARKET_US));
    }

    @Test
    @DisplayName("China regular hours inferred as REGULAR (10:00 Beijing)")
    void testInferCNRegular() {
        // 02:00 UTC = 10:00 Asia/Shanghai (trading window 09:15-11:30)
        service.setClock(Clock.fixed(Instant.parse("2026-06-26T02:00:00Z"), ZoneId.systemDefault()));
        assertEquals(TradingSession.REGULAR, service.inferSession(StockInfo.MARKET_CN_SH));
    }

    @Test
    @DisplayName("Weekend inferred as CLOSED for all markets")
    void testInferWeekendClosed() {
        assertEquals(TradingSession.CLOSED, service.inferSession(StockInfo.MARKET_US));
        assertEquals(TradingSession.CLOSED, service.inferSession(StockInfo.MARKET_CN_SH));
        assertEquals(TradingSession.CLOSED, service.inferSession(StockInfo.MARKET_HK));
    }
}
