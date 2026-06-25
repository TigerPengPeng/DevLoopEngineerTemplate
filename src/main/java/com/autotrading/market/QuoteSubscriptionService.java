package com.autotrading.market;

import com.autotrading.futu.AsyncRequestBridge;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.model.StockInfo;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.pb.Common;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subscribes to real-time basic quotes and restores subscriptions after reconnect.
 */
@Service
public class QuoteSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(QuoteSubscriptionService.class);
    private static final int BATCH_SIZE = 50;

    private final FutuConnectionManager connectionManager;
    private final AsyncRequestBridge bridge;

    /** Stocks that have been subscribed (for reconnect recovery). */
    private final CopyOnWriteArrayList<StockInfo> subscribedStocks = new CopyOnWriteArrayList<>();

    /** Count of stocks actually confirmed subscribed by OpenD in the last call. */
    private volatile int lastConfirmedCount = 0;

    public QuoteSubscriptionService(FutuConnectionManager connectionManager, AsyncRequestBridge bridge) {
        this.connectionManager = connectionManager;
        this.bridge = bridge;
    }

    /**
     * Subscribes to basic quotes for the given stocks.
     * Processes in batches to respect OpenD subscription limits.
     * Returns the count of stocks that OpenD actually confirmed subscribed.
     */
    public int subscribeAll(List<StockInfo> stocks) {
        subscribedStocks.clear();
        subscribedStocks.addAll(stocks);

        List<List<StockInfo>> batches = partition(stocks, BATCH_SIZE);
        int totalSubscribed = 0;

        for (int i = 0; i < batches.size(); i++) {
            List<StockInfo> batch = batches.get(i);
            try {
                int count = subscribeBatch(batch);
                totalSubscribed += count;
            } catch (Exception e) {
                log.warn("Batch {} subscription failed: {}", i + 1, e.getMessage());
            }
        }
        lastConfirmedCount = totalSubscribed;
        log.info("Subscription complete: {}/{} stocks subscribed", totalSubscribed, stocks.size());
        return totalSubscribed;
    }

    private int subscribeBatch(List<StockInfo> batch) throws AsyncRequestBridge.FutuRequestException {
        FTAPI_Conn_Qot conn = connectionManager.getConnQot();
        if (conn == null) {
            throw new AsyncRequestBridge.FutuRequestException("Not connected to OpenD");
        }

        List<QotCommon.Security> securityList = new ArrayList<>();
        for (StockInfo stock : batch) {
            securityList.add(QotCommon.Security.newBuilder()
                    .setMarket(stock.getMarket())
                    .setCode(stock.getCode())
                    .build());
        }

        QotSub.Request request = QotSub.Request.newBuilder()
                .setC2S(QotSub.C2S.newBuilder()
                        .addAllSecurityList(securityList)
                        .addSubTypeList(QotCommon.SubType.SubType_Basic_VALUE)
                        .setIsSubOrUnSub(true)
                        .setIsRegOrUnRegPush(true)
                        .setIsFirstPush(true)
                        .build())
                .build();

        int serial = conn.sub(request);
        QotSub.Response response = bridge.await(serial, QotSub.Response.class);

        if (response.getRetType() != Common.RetType.RetType_Succeed_VALUE) {
            throw new AsyncRequestBridge.FutuRequestException("Sub failed: " + response.getRetMsg());
        }

        log.debug("Subscribed batch of {} stocks", batch.size());
        return batch.size();
    }

    /**
     * Restores all subscriptions after a reconnect.
     */
    public int resubscribeAll() {
        if (subscribedStocks.isEmpty()) {
            log.info("No stocks to resubscribe");
            return 0;
        }
        log.info("Restoring subscriptions for {} stocks after reconnect", subscribedStocks.size());
        return subscribeAll(new ArrayList<>(subscribedStocks));
    }

    public List<StockInfo> getSubscribedStocks() {
        return new ArrayList<>(subscribedStocks);
    }

    /**
     * Returns the count confirmed by OpenD in the last subscribeAll call.
     */
    public int getLastConfirmedCount() {
        return lastConfirmedCount;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
