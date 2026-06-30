package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.StockInfo;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.Common;
import com.futu.openapi.pb.QotCommon.*;
import com.futu.openapi.pb.QotRequestHistoryKL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KLineService {

    private static final Logger log = LoggerFactory.getLogger(KLineService.class);
    private static final int MAX_KL_COUNT = 120;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Frequency labels used across the app. */
    public static final String FREQ_DAY = "day";
    public static final String FREQ_WEEK = "week";

    /** Cache-key prefix for weekly bars, to namespace daily (bare stockKey) from weekly. */
    private static final String WEEK_PREFIX = "W:";

    private final FutuConnectionManager connectionManager;
    private final AsyncRequestBridge bridge;

    private final Map<String, List<Double>> closePriceCache = new ConcurrentHashMap<>();
    private final Map<String, List<KLineData>> klineCache = new ConcurrentHashMap<>();

    public KLineService(FutuConnectionManager connectionManager, AsyncRequestBridge bridge) {
        this.connectionManager = connectionManager;
        this.bridge = bridge;
    }

    public List<Double> fetchDailyCloses(StockInfo stock) throws AsyncRequestBridge.FutuRequestException {
        List<KLineData> klines = fetchKLines(stock);
        List<Double> closes = new ArrayList<>();
        for (KLineData k : klines) closes.add(k.close());
        return closes;
    }

    public List<KLineData> fetchKLines(StockInfo stock) throws AsyncRequestBridge.FutuRequestException {
        return fetchKLines(stock, KLType.KLType_Day_VALUE);
    }

    /**
     * Fetches K-lines for a stock at the given Futu KL type value (e.g.
     * {@link KLType#KLType_Day_VALUE}, {@link KLType#KLType_Week_VALUE}).
     * The date window is widened for lower-frequency bars so maxAckKLNum
     * returns the most recent bars.
     */
    public List<KLineData> fetchKLines(StockInfo stock, int klTypeValue) throws AsyncRequestBridge.FutuRequestException {
        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) throw new AsyncRequestBridge.FutuRequestException("Not connected to OpenD");

        String endDate = LocalDate.now().format(DATE_FMT);
        // Day: 120 trading days ~= 175 calendar days. Week: 120 weeks ~= 900 days.
        long lookbackDays = klTypeValue == KLType.KLType_Week_VALUE ? 900L : 175L;
        String beginDate = LocalDate.now().minusDays(lookbackDays).format(DATE_FMT);

        QotRequestHistoryKL.Request request = QotRequestHistoryKL.Request.newBuilder()
                .setC2S(QotRequestHistoryKL.C2S.newBuilder()
                        .setSecurity(Security.newBuilder()
                                .setMarket(stock.getMarket()).setCode(stock.getCode()).build())
                        .setKlType(klTypeValue)
                        .setRehabType(RehabType.RehabType_Forward_VALUE)
                        .setBeginTime(beginDate).setEndTime(endDate)
                        .setMaxAckKLNum(MAX_KL_COUNT).build())
                .build();

        int serial = conn.requestHistoryKL(request);
        QotRequestHistoryKL.Response response = bridge.await(serial, QotRequestHistoryKL.Response.class);
        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE)
            throw new AsyncRequestBridge.FutuRequestException("RequestHistoryKL failed: " + response.getRetMsg());

        List<KLineData> klines = new ArrayList<>();
        for (KLine kl : response.getS2C().getKlListList()) {
            if (kl.getIsBlank()) continue;
            klines.add(new KLineData(kl.getTime(), kl.getOpenPrice(), kl.getHighPrice(),
                    kl.getLowPrice(), kl.getClosePrice(), kl.getVolume(), kl.getChangeRate()));
        }
        String cacheKey = cacheKey(stock.key(), klTypeValue);
        klineCache.put(cacheKey, klines);
        closePriceCache.put(cacheKey, klines.stream().map(KLineData::close).toList());
        log.debug("Fetched {} {} klines for {}", klines.size(),
                klTypeValue == KLType.KLType_Week_VALUE ? "weekly" : "daily", stock.key());
        return klines;
    }

    /** Fetches K-lines for the given frequency label ("day" or "week"). */
    public List<KLineData> fetchKLines(StockInfo stock, String frequency) throws AsyncRequestBridge.FutuRequestException {
        return fetchKLines(stock, klTypeValue(frequency));
    }

    public List<KLineData> getKLines(int market, String code) {
        return getKLines(market, code, FREQ_DAY);
    }

    /**
     * Returns K-lines for the given frequency. Always fetches fresh data so the
     * detail page shows the latest bars.
     */
    public List<KLineData> getKLines(int market, String code, String frequency) {
        String key = market + "." + code;
        try { return fetchKLines(new StockInfo(market, code, code), frequency); }
        catch (Exception e) { log.warn("Failed to fetch klines for {}: {}", key, e.getMessage()); return List.of(); }
    }

    public void fetchAll(List<StockInfo> stocks) {
        fetchAll(stocks, FREQ_DAY);
    }

    /** Fetches and caches closes for all stocks at the given frequency. */
    public void fetchAll(List<StockInfo> stocks, String frequency) {
        int success = 0;
        for (StockInfo s : stocks) {
            try { fetchKLines(s, frequency); success++; }
            catch (Exception e) { log.warn("Failed to fetch {} K-lines for {}: {}",
                    frequency, s.key(), e.getMessage()); }
        }
        log.info("{} K-line fetch complete: {}/{} stocks", frequency, success, stocks.size());
    }

    public List<Double> getCloses(String stockKey) {
        return getCloses(stockKey, FREQ_DAY);
    }

    /** Returns cached closes for a stock at the given frequency (empty if none). */
    public List<Double> getCloses(String stockKey, String frequency) {
        return closePriceCache.getOrDefault(cacheKey(stockKey, klTypeValue(frequency)), List.of());
    }

    public void refreshAll(List<StockInfo> stocks) {
        fetchAll(stocks);
    }

    public int cachedStockCount() { return closePriceCache.size(); }

    /** Maps a frequency label to the Futu KL type value. */
    private int klTypeValue(String frequency) {
        return FREQ_WEEK.equalsIgnoreCase(frequency) ? KLType.KLType_Week_VALUE : KLType.KLType_Day_VALUE;
    }

    /** Cache key namespaces daily (bare) from weekly (prefixed). */
    private String cacheKey(String stockKey, int klTypeValue) {
        return klTypeValue == KLType.KLType_Week_VALUE ? WEEK_PREFIX + stockKey : stockKey;
    }

    public record KLineData(String time, double open, double high, double low, double close,
                            long volume, double changeRate) {}
}
