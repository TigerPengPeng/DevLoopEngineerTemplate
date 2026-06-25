package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.futu.QuoteUpdateListener;
import com.autotrading.model.StockInfo;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.Common;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fallback price source when push subscriptions are unavailable (insufficient quote permission).
 * Polls getSecuritySnapshot periodically and feeds results into the same QuoteProcessor pipeline.
 */
@Service
public class SnapshotPollingService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPollingService.class);

    private final FutuConnectionManager connectionManager;
    private final AsyncRequestBridge bridge;

    private QuoteUpdateListener listener;
    private List<StockInfo> stocks = List.of();
    private volatile boolean polling = false;

    public SnapshotPollingService(FutuConnectionManager connectionManager, AsyncRequestBridge bridge) {
        this.connectionManager = connectionManager;
        this.bridge = bridge;
    }

    public void setListener(QuoteUpdateListener listener) {
        this.listener = listener;
    }

    public void setStocks(List<StockInfo> stocks) {
        this.stocks = stocks;
    }

    public void setPolling(boolean enabled) {
        boolean wasPolling = this.polling;
        this.polling = enabled;
        if (enabled && !wasPolling) {
            log.info("Snapshot polling enabled for {} stocks (push subscription fallback)", stocks.size());
        } else if (!enabled && wasPolling) {
            log.info("Snapshot polling disabled");
        }
    }

    public boolean isPolling() {
        return polling;
    }

    @Scheduled(fixedDelayString = "${futu.monitor.snapshot-poll-interval:10000}")
    public void pollSnapshots() {
        if (!polling || !connectionManager.isReady() || stocks.isEmpty() || listener == null) {
            return;
        }

        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) {
            return;
        }

        // Try batch first; if it fails (e.g. one stock has unsupported market), fall back to per-stock
        int fetched;
        try {
            fetched = pollBatch(conn, stocks);
        } catch (Exception batchErr) {
            log.debug("Batch snapshot failed, falling back to per-stock: {}", batchErr.getMessage());
            fetched = pollIndividual(conn);
        }

        if (fetched > 0) {
            log.debug("Snapshot poll: {}/{} stocks updated", fetched, stocks.size());
        }
    }

    /**
     * Query stocks one-by-one so an unsupported market doesn't fail the whole batch.
     */
    private int pollIndividual(FTAPI_Conn_Qot conn) {
        int count = 0;
        for (StockInfo stock : stocks) {
            try {
                count += pollBatch(conn, List.of(stock));
            } catch (Exception e) {
                log.debug("Snapshot failed for {}: {}", stock.key(), e.getMessage());
            }
        }
        return count;
    }

    private int pollBatch(FTAPI_Conn_Qot conn, List<StockInfo> batch)
            throws AsyncRequestBridge.FutuRequestException {

        java.util.List<QotCommon.Security> securityList = new java.util.ArrayList<>();
        for (StockInfo stock : batch) {
            securityList.add(QotCommon.Security.newBuilder()
                    .setMarket(stock.getMarket())
                    .setCode(stock.getCode())
                    .build());
        }

        QotGetSecuritySnapshot.Request request = QotGetSecuritySnapshot.Request.newBuilder()
                .setC2S(QotGetSecuritySnapshot.C2S.newBuilder()
                        .addAllSecurityList(securityList)
                        .build())
                .build();

        int serial = conn.getSecuritySnapshot(request);
        QotGetSecuritySnapshot.Response response = bridge.await(serial, QotGetSecuritySnapshot.Response.class);

        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE) {
            throw new AsyncRequestBridge.FutuRequestException("GetSecuritySnapshot failed: " + response.getRetMsg());
        }

        int count = 0;
        for (QotGetSecuritySnapshot.Snapshot snapshot : response.getS2C().getSnapshotListList()) {
            if (!snapshot.hasBasic()) {
                continue;
            }
            QotGetSecuritySnapshot.SnapshotBasicData basic = snapshot.getBasic();
            if (!basic.hasSecurity() || !basic.hasCurPrice()) {
                continue;
            }

            int market = basic.getSecurity().getMarket();
            String code = basic.getSecurity().getCode();
            String stockKey = market + "." + code;
            double curPrice = basic.getCurPrice();
            double lastClose = basic.hasLastClosePrice() ? basic.getLastClosePrice() : 0;

            // Look up stock name from registry
            String name = batch.stream()
                    .filter(s -> s.getMarket() == market && s.getCode().equals(code))
                    .map(StockInfo::getName)
                    .findFirst()
                    .orElse(code);

            listener.onQuoteUpdate(stockKey, name, curPrice, lastClose);
            count++;
        }
        return count;
    }
}
