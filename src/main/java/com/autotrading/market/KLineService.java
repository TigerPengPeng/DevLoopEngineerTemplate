package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.StockInfo;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.Common;
import com.futu.openapi.pb.QotCommon;
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
        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) throw new AsyncRequestBridge.FutuRequestException("Not connected to OpenD");

        String endDate = LocalDate.now().format(DATE_FMT);
        // 120 trading days ~= 168 calendar days; use 175 for holiday buffer
        // so maxAckKLNum returns the most recent bars, not the oldest
        String beginDate = LocalDate.now().minusDays(175).format(DATE_FMT);

        QotRequestHistoryKL.Request request = QotRequestHistoryKL.Request.newBuilder()
                .setC2S(QotRequestHistoryKL.C2S.newBuilder()
                        .setSecurity(QotCommon.Security.newBuilder()
                                .setMarket(stock.getMarket()).setCode(stock.getCode()).build())
                        .setKlType(QotCommon.KLType.KLType_Day_VALUE)
                        .setRehabType(QotCommon.RehabType.RehabType_Forward_VALUE)
                        .setBeginTime(beginDate).setEndTime(endDate)
                        .setMaxAckKLNum(MAX_KL_COUNT).build())
                .build();

        int serial = conn.requestHistoryKL(request);
        QotRequestHistoryKL.Response response = bridge.await(serial, QotRequestHistoryKL.Response.class);
        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE)
            throw new AsyncRequestBridge.FutuRequestException("RequestHistoryKL failed: " + response.getRetMsg());

        List<KLineData> klines = new ArrayList<>();
        for (QotCommon.KLine kl : response.getS2C().getKlListList()) {
            if (kl.getIsBlank()) continue;
            klines.add(new KLineData(kl.getTime(), kl.getOpenPrice(), kl.getHighPrice(),
                    kl.getLowPrice(), kl.getClosePrice(), kl.getVolume(), kl.getChangeRate()));
        }
        klineCache.put(stock.key(), klines);
        closePriceCache.put(stock.key(), klines.stream().map(KLineData::close).toList());
        log.debug("Fetched {} daily klines for {}", klines.size(), stock.key());
        return klines;
    }

    public List<KLineData> getKLines(int market, String code) {
        String key = market + "." + code;
        // Always fetch fresh data so the detail page shows the latest 120 trading days
        try { return fetchKLines(new StockInfo(market, code, code)); }
        catch (Exception e) { log.warn("Failed to fetch klines for {}: {}", key, e.getMessage()); return List.of(); }
    }

    public void fetchAll(List<StockInfo> stocks) {
        int success = 0;
        for (StockInfo s : stocks) {
            try { fetchDailyCloses(s); success++; }
            catch (Exception e) { log.warn("Failed to fetch K-lines for {}: {}", s.key(), e.getMessage()); }
        }
        log.info("K-line fetch complete: {}/{} stocks", success, stocks.size());
    }

    public List<Double> getCloses(String stockKey) { return closePriceCache.getOrDefault(stockKey, List.of()); }
    public void refreshAll(List<StockInfo> stocks) { fetchAll(stocks); }
    public int cachedStockCount() { return closePriceCache.size(); }

    public record KLineData(String time, double open, double high, double low, double close,
                            long volume, double changeRate) {}
}
