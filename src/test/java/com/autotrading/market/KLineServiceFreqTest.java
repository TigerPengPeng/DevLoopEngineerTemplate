package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.StockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class KLineServiceFreqTest {

    private KLineService service;
    private Map<String, List<Double>> closeCache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        FutuConnectionManager connMgr = mock(FutuConnectionManager.class);
        AsyncRequestBridge bridge = mock(AsyncRequestBridge.class);
        service = new KLineService(connMgr, bridge);
        Field f = KLineService.class.getDeclaredField("closePriceCache");
        f.setAccessible(true);
        closeCache = (Map<String, List<Double>>) f.get(service);
    }

    @Test
    @DisplayName("Day closes use bare stockKey; week closes are namespaced with W: prefix")
    void testCacheNamespacing() {
        closeCache.put("11.AAPL", List.of(100.0, 101.0, 102.0));
        closeCache.put("W:11.AAPL", List.of(90.0, 95.0, 98.0));

        // Day frequency (default) reads bare key
        assertEquals(List.of(100.0, 101.0, 102.0), service.getCloses("11.AAPL"));
        assertEquals(List.of(100.0, 101.0, 102.0), service.getCloses("11.AAPL", "day"));

        // Week frequency reads namespaced key
        assertEquals(List.of(90.0, 95.0, 98.0), service.getCloses("11.AAPL", "week"));
    }

    @Test
    @DisplayName("Unknown frequency falls back to day")
    void testUnknownFrequencyDefaultsToDay() {
        closeCache.put("11.AAPL", List.of(100.0));
        // "monthly" is not supported -> should fall back to day
        assertEquals(List.of(100.0), service.getCloses("11.AAPL", "monthly"));
    }

    @Test
    @DisplayName("Missing frequency returns empty list, not null")
    void testMissingReturnsEmpty() {
        assertTrue(service.getCloses("11.UNKNOWN", "week").isEmpty());
        assertTrue(service.getCloses("11.UNKNOWN").isEmpty());
    }
}
