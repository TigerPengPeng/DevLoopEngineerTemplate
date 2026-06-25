package com.autotrading.futu;

import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTSPI_Conn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FTSPI_Conn implementation: handles connection lifecycle events.
 * Delegates connection/disconnection notifications to a listener.
 */
public class FutuConnHandler implements FTSPI_Conn {

    private static final Logger log = LoggerFactory.getLogger(FutuConnHandler.class);

    private final ConnectionStateListener listener;

    public FutuConnHandler(ConnectionStateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onInitConnect(FTAPI_Conn conn, long errCode, String desc) {
        if (errCode == 0) {
            log.info("Futu OpenD connected (connID={})", conn.getConnectID());
            if (listener != null) {
                listener.onConnected();
            }
        } else {
            log.error("Futu OpenD connect failed (errCode={}, desc={})", errCode, desc);
            if (listener != null) {
                listener.onConnectFailed(errCode, desc);
            }
        }
    }

    @Override
    public void onDisconnect(FTAPI_Conn conn, long errCode) {
        log.warn("OpenD disconnected (connID={}, errCode={}), initiating reconnect", conn.getConnectID(), errCode);
        if (listener != null) {
            listener.onDisconnected(errCode);
        }
    }

    /**
     * Listener interface for connection state changes.
     */
    public interface ConnectionStateListener {
        void onConnected();
        void onConnectFailed(long errCode, String desc);
        void onDisconnected(long errCode);
    }
}
