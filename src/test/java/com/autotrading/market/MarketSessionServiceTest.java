package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.TradingSession;
import com.futu.openapi.pb.QotCommon;
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
}
