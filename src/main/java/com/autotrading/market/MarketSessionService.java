package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.StockInfo;
import com.autotrading.model.TradingSession;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.Common;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetMarketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks market state per market and maps Futu QotMarketState to TradingSession.
 * Supports US (pre-market, regular, after-hours, overnight) and China markets.
 * <p>
 * Falls back to time-based inference when getMarketState is unavailable
 * (insufficient quote permission or unsupported market).
 */
@Service
public class MarketSessionService {

    private static final Logger log = LoggerFactory.getLogger(MarketSessionService.class);

    private final FutuConnectionManager connectionManager;
    private final AsyncRequestBridge bridge;

    /** Time source for fallback inference; overridable in tests. */
    private Clock clock = Clock.systemDefaultZone();

    /** Cache: market code -> current trading session. */
    private final Map<Integer, TradingSession> sessionCache = new ConcurrentHashMap<>();

    /** Representative stock per market for state queries. */
    private final Map<Integer, QotCommon.Security> marketProxies = new ConcurrentHashMap<>();

    /** Markets that OpenD supports for getMarketState queries. */
    private static final Set<Integer> SUPPORTED_MARKETS = Set.of(
            1, 11, 21, 22  // HK, US, SH, SZ
    );

    public MarketSessionService(FutuConnectionManager connectionManager, AsyncRequestBridge bridge) {
        this.connectionManager = connectionManager;
        this.bridge = bridge;
    }

    /** Test seam: pins the time source so fallback inference is deterministic. */
    void setClock(Clock clock) { this.clock = clock; }

    /**
     * Registers representative stocks per market for state queries.
     */
    public void registerStocks(List<StockInfo> stocks) {
        for (StockInfo stock : stocks) {
            marketProxies.putIfAbsent(stock.getMarket(), QotCommon.Security.newBuilder()
                    .setMarket(stock.getMarket())
                    .setCode(stock.getCode())
                    .build());
        }
    }

    /**
     * Polls market state per market individually so one unsupported market
     * doesn't fail the entire batch.
     */
    public void pollMarketState() {
        if (marketProxies.isEmpty()) {
            return;
        }

        int queried = 0;
        for (Map.Entry<Integer, QotCommon.Security> entry : marketProxies.entrySet()) {
            int market = entry.getKey();
            if (!SUPPORTED_MARKETS.contains(market)) {
                sessionCache.put(market, inferSession(market));
                continue;
            }
            try {
                TradingSession session = queryMarketState(market, entry.getValue());
                // If OpenD says CLOSED but time inference says trading, trust the clock
                if (session == TradingSession.CLOSED) {
                    TradingSession inferred = inferSession(market);
                    if (inferred != TradingSession.CLOSED) {
                        session = inferred;
                    }
                }
                TradingSession previous = sessionCache.put(market, session);
                if (previous != session) {
                    log.info("Market {} session: {} -> {}", market,
                            previous != null ? previous : "N/A", session);
                }
                queried++;
            } catch (Exception e) {
                log.debug("Market state query failed for market {}: {}, using time-based inference",
                        market, e.getMessage());
                sessionCache.put(market, inferSession(market));
            }
        }

        if (queried > 0) {
            log.debug("Market state poll: {}/{} markets queried via OpenD", queried, marketProxies.size());
        }
    }

    private TradingSession queryMarketState(int market, QotCommon.Security proxy)
            throws AsyncRequestBridge.FutuRequestException {
        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) {
            return inferSession(market);
        }

        QotGetMarketState.Request request = QotGetMarketState.Request.newBuilder()
                .setC2S(QotGetMarketState.C2S.newBuilder()
                        .addSecurityList(proxy)
                        .build())
                .build();

        int serial = conn.getMarketState(request);
        QotGetMarketState.Response response = bridge.await(serial, QotGetMarketState.Response.class);

        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE) {
            throw new AsyncRequestBridge.FutuRequestException(response.getRetMsg());
        }

        for (QotGetMarketState.MarketInfo info : response.getS2C().getMarketInfoListList()) {
            return mapMarketState(market, info.getMarketState());
        }
        return inferSession(market);
    }

    /**
     * Maps a Futu QotMarketState value to a TradingSession.
     */
    public TradingSession mapMarketState(int market, int stateValue) {
        QotCommon.QotMarketState state = QotCommon.QotMarketState.forNumber(stateValue);
        if (state == null) {
            return TradingSession.CLOSED;
        }

        switch (state) {
            case QotMarketState_PreMarketBegin:
            case QotMarketState_PreMarketEnd:
                return TradingSession.PRE_MARKET;
            case QotMarketState_Morning:
            case QotMarketState_Auction:
            case QotMarketState_Afternoon:
                return TradingSession.REGULAR;
            case QotMarketState_AfterHoursBegin:
            case QotMarketState_AfterHoursEnd:
                return TradingSession.AFTER_HOURS;
            case QotMarketState_NightOpen:
            case QotMarketState_NightEnd:
                return TradingSession.OVERNIGHT;
            case QotMarketState_Closed:
            case QotMarketState_WaitingOpen:
            case QotMarketState_None:
                return TradingSession.CLOSED;
            case QotMarketState_StibAfterHoursBegin:
            case QotMarketState_StibAfterHoursEnd:
            case QotMarketState_StibAfterHoursWait:
                return TradingSession.AFTER_HOURS;
            case QotMarketState_Rest:
                return TradingSession.REGULAR;
            default:
                return TradingSession.CLOSED;
        }
    }

    /**
     * Time-based fallback: infers the trading session from current time.
     * Used when getMarketState is unavailable (insufficient permission or unsupported market).
     */
    public TradingSession inferSession(int market) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        DayOfWeek dow = now.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        switch (market) {
            case StockInfo.MARKET_US:
                return inferUSSession(now, weekend);
            case StockInfo.MARKET_CN_SH:
            case StockInfo.MARKET_CN_SZ:
                return inferCNSession(now.withZoneSameInstant(ZoneId.of("Asia/Shanghai")), weekend);
            case StockInfo.MARKET_HK:
                return inferHKSession(now.withZoneSameInstant(ZoneId.of("Asia/Hong_Kong")), weekend);
            default:
                // For forex/futures/etc, assume active if weekday
                return weekend ? TradingSession.CLOSED : TradingSession.REGULAR;
        }
    }

    private TradingSession inferUSSession(ZonedDateTime now, boolean weekend) {
        if (weekend) {
            return TradingSession.CLOSED;
        }
        ZonedDateTime nyTime = now.withZoneSameInstant(ZoneId.of("America/New_York"));
        double timeMin = nyTime.getHour() * 60.0 + nyTime.getMinute();
        // ET: pre 4:00-9:30, regular 9:30-16:00, after 16:00-20:00, overnight 20:00-4:00
        if (timeMin >= 240 && timeMin < 570) {
            return TradingSession.PRE_MARKET;
        } else if (timeMin >= 570 && timeMin < 960) {
            return TradingSession.REGULAR;
        } else if (timeMin >= 960 && timeMin < 1200) {
            return TradingSession.AFTER_HOURS;
        } else if (timeMin >= 1200 || timeMin < 240) {
            return TradingSession.OVERNIGHT;
        }
        return TradingSession.CLOSED;
    }

    private TradingSession inferCNSession(ZonedDateTime cnTime, boolean weekend) {
        if (weekend) {
            return TradingSession.CLOSED;
        }
        double timeMin = cnTime.getHour() * 60.0 + cnTime.getMinute();
        // Beijing: 9:15-11:30, 13:00-15:00
        if ((timeMin >= 555 && timeMin < 690) || (timeMin >= 780 && timeMin < 900)) {
            return TradingSession.REGULAR;
        }
        return TradingSession.CLOSED;
    }

    private TradingSession inferHKSession(ZonedDateTime hkTime, boolean weekend) {
        if (weekend) {
            return TradingSession.CLOSED;
        }
        double timeMin = hkTime.getHour() * 60.0 + hkTime.getMinute();
        // HKT: 9:30-12:00, 13:00-16:00
        if ((timeMin >= 570 && timeMin < 720) || (timeMin >= 780 && timeMin < 960)) {
            return TradingSession.REGULAR;
        }
        return TradingSession.CLOSED;
    }

    /**
     * Returns the current trading session for a market.
     */
    public TradingSession getSession(int market) {
        TradingSession cached = sessionCache.get(market);
        if (cached != null) {
            return cached;
        }
        return inferSession(market);
    }

    /**
     * Returns whether the market is in a trading session (not closed).
     */
    public boolean isTrading(int market) {
        return getSession(market).isTrading();
    }

    /**
     * Resets all cached sessions (used on reconnect).
     */
    public void resetAll() {
        sessionCache.clear();
    }
}
