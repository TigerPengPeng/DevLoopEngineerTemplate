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

/**
 * Fetches and caches daily K-line (close price) data for MA calculation.
 */
@Service
public class KLineService {

    private static final Logger log = LoggerFactory.getLogger(KLineService.class);
    private static final int MAX_KL_COUNT = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FutuConnectionManager connectionManager;
    private final AsyncRequestBridge bridge;

    /** Cache: stockKey -> list of close prices (chronological order). */
    private final Map<String, List<Double>> closePriceCache = new ConcurrentHashMap<>();

    public KLineService(FutuConnectionManager connectionManager, AsyncRequestBridge bridge) {
        this.connectionManager = connectionManager;
        this.bridge = bridge;
    }

    /**
     * Fetches the latest daily K-lines and updates the cache for one stock.
     */
    public List<Double> fetchDailyCloses(StockInfo stock) throws AsyncRequestBridge.FutuRequestException {
        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) {
            throw new AsyncRequestBridge.FutuRequestException("Not connected to OpenD");
        }

        String endDate = LocalDate.now().format(DATE_FMT);
        String beginDate = LocalDate.now().minusDays(MAX_KL_COUNT * 2).format(DATE_FMT);

        QotRequestHistoryKL.Request request = QotRequestHistoryKL.Request.newBuilder()
                .setC2S(QotRequestHistoryKL.C2S.newBuilder()
                        .setSecurity(QotCommon.Security.newBuilder()
                                .setMarket(stock.getMarket())
                                .setCode(stock.getCode())
                                .build())
                        .setKlType(QotCommon.KLType.KLType_Day_VALUE)
                        .setRehabType(QotCommon.RehabType.RehabType_Forward_VALUE)
                        .setBeginTime(beginDate)
                        .setEndTime(endDate)
                        .setMaxAckKLNum(MAX_KL_COUNT)
                        .build())
                .build();

        int serial = conn.requestHistoryKL(request);
        QotRequestHistoryKL.Response response = bridge.await(serial, QotRequestHistoryKL.Response.class);

        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE) {
            throw new AsyncRequestBridge.FutuRequestException(
                    "RequestHistoryKL failed for " + stock.key() + ": " + response.getRetMsg());
        }

        List<Double> closes = new ArrayList<>();
        for (QotCommon.KLine kl : response.getS2C().getKlListList()) {
            if (kl.hasClosePrice() && !kl.getIsBlank()) {
                closes.add(kl.getClosePrice());
            }
        }

        closePriceCache.put(stock.key(), closes);
        log.debug("Fetched {} daily closes for {}", closes.size(), stock.key());
        return closes;
    }

    /**
     * Batch fetch for multiple stocks.
     */
    public void fetchAll(List<StockInfo> stocks) {
        int success = 0;
        for (StockInfo stock : stocks) {
            try {
                fetchDailyCloses(stock);
                success++;
            } catch (Exception e) {
                log.warn("Failed to fetch K-lines for {}: {}", stock.key(), e.getMessage());
            }
        }
        log.info("K-line fetch complete: {}/{} stocks", success, stocks.size());
    }

    /**
     * Returns cached close prices for a stock.
     */
    public List<Double> getCloses(String stockKey) {
        return closePriceCache.getOrDefault(stockKey, List.of());
    }

    /**
     * Refreshes all cached stocks (called periodically).
     */
    public void refreshAll(List<StockInfo> stocks) {
        fetchAll(stocks);
    }

    public int cachedStockCount() {
        return closePriceCache.size();
    }
}
