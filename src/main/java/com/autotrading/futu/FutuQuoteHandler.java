package com.autotrading.futu;

import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FTSPI_Qot implementation: routes async replies to the bridge and
 * dispatches real-time quote pushes to the registered listener.
 */
@Component
public class FutuQuoteHandler implements FTSPI_Qot {

    private static final Logger log = LoggerFactory.getLogger(FutuQuoteHandler.class);

    private final AsyncRequestBridge bridge;
    private volatile QuoteUpdateListener quoteListener;

    public FutuQuoteHandler(AsyncRequestBridge bridge) {
        this.bridge = bridge;
    }

    public void setQuoteListener(QuoteUpdateListener listener) {
        this.quoteListener = listener;
    }

    @Override
    public void onReply_GetUserSecurityGroup(FTAPI_Conn conn, int serial, QotGetUserSecurityGroup.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_GetUserSecurity(FTAPI_Conn conn, int serial, QotGetUserSecurity.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_RequestHistoryKL(FTAPI_Conn conn, int serial, QotRequestHistoryKL.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_GetMarketState(FTAPI_Conn conn, int serial, QotGetMarketState.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_Sub(FTAPI_Conn conn, int serial, QotSub.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_GetBasicQot(FTAPI_Conn conn, int serial, QotGetBasicQot.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onReply_GetSecuritySnapshot(FTAPI_Conn conn, int serial, QotGetSecuritySnapshot.Response response) {
        bridge.complete(serial, response);
    }

    @Override
    public void onPush_UpdateBasicQuote(FTAPI_Conn conn, QotUpdateBasicQot.Response response) {
        if (response == null || !response.hasS2C()) {
            return;
        }
        QotUpdateBasicQot.S2C s2c = response.getS2C();
        for (QotCommon.BasicQot qot : s2c.getBasicQotListList()) {
            if (!qot.hasCurPrice() || !qot.hasSecurity()) {
                continue;
            }
            double curPrice = qot.getCurPrice();
            double preClose = qot.hasLastClosePrice() ? qot.getLastClosePrice() : 0.0;
            String stockKey = qot.getSecurity().getMarket() + "." + qot.getSecurity().getCode();
            String name = qot.hasName() ? qot.getName() : qot.getSecurity().getCode();

            if (quoteListener != null) {
                try {
                    quoteListener.onQuoteUpdate(stockKey, name, curPrice, preClose);
                } catch (Exception e) {
                    log.error("Error in quote listener for {}: {}", stockKey, e.getMessage(), e);
                }
            }
        }
    }
}
