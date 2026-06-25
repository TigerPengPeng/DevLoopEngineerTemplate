package com.autotrading.futu;

import com.autotrading.config.FutuProperties;
import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.ConnStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the Futu OpenD connection lifecycle: connect, disconnect detection,
 * automatic reconnect with exponential backoff, and clean resource release.
 * <p>
 * Memory safety: old connection objects are close()'d and set to null before
 * creating new ones, preventing resource leaks across reconnect cycles.
 */
@Component
public class FutuConnectionManager implements FutuConnHandler.ConnectionStateListener {

    private static final Logger log = LoggerFactory.getLogger(FutuConnectionManager.class);

    private final FutuProperties properties;
    private final AsyncRequestBridge requestBridge;
    private final FutuQuoteHandler quoteHandler;

    private volatile FTAPI_Conn_Qot connQot;
    private volatile FTAPI_Conn_Trd connTrd;

    private final ReentrantLock reconnectLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "futu-reconnect");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean apiInitialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    private volatile CountDownLatch connectLatch;
    private volatile long connectErrCode = 0;

    /** Callback invoked after a successful reconnect to restore subscriptions. */
    private Runnable postReconnectHook;

    public FutuConnectionManager(FutuProperties properties, AsyncRequestBridge requestBridge,
                                  FutuQuoteHandler quoteHandler) {
        this.properties = properties;
        this.requestBridge = requestBridge;
        this.quoteHandler = quoteHandler;
    }

    @PostConstruct
    public void init() {
        if (!apiInitialized.compareAndSet(false, true)) {
            return;
        }
        log.info("Initializing Futu OpenD connection to {}:{}",
                properties.getOpend().getIp(), properties.getOpend().getPort());
        FTAPI.init();
        connect();
    }

    /**
     * Creates new connection objects and connects to OpenD.
     */
    public synchronized void connect() {
        if (shuttingDown.get()) {
            return;
        }

        FutuConnHandler connHandler = new FutuConnHandler(this);
        connectLatch = new CountDownLatch(1);
        connectErrCode = 0;

        connQot = new FTAPI_Conn_Qot();
        connQot.setConnSpi(connHandler);
        connQot.setQotSpi(quoteHandler);

        boolean started = connQot.initConnect(
                properties.getOpend().getIp(),
                properties.getOpend().getPort(),
                properties.getOpend().isEncrypt()
        );

        if (!started) {
            log.error("Failed to start connection to OpenD {}:{}",
                    properties.getOpend().getIp(), properties.getOpend().getPort());
            scheduleReconnect();
            return;
        }

        try {
            if (!connectLatch.await(15, TimeUnit.SECONDS)) {
                log.warn("Connection to OpenD timed out, will retry");
                scheduleReconnect();
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Connection wait interrupted");
            return;
        }

        if (connectErrCode != 0) {
            log.error("OpenD connection rejected (errCode={}), will retry", connectErrCode);
            scheduleReconnect();
        }
    }

    // ---- ConnectionStateListener ----

    @Override
    public void onConnected() {
        if (connectLatch != null) {
            connectLatch.countDown();
        }
        reconnectAttempt.set(0);
        boolean wasReconnect = connTrd != null; // simplified check
        if (wasReconnect) {
            log.info("Reconnected successfully");
            if (postReconnectHook != null) {
                try {
                    postReconnectHook.run();
                } catch (Exception e) {
                    log.error("Post-reconnect hook failed: {}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void onConnectFailed(long errCode, String desc) {
        connectErrCode = errCode;
        if (connectLatch != null) {
            connectLatch.countDown();
        }
    }

    @Override
    public void onDisconnected(long errCode) {
        if (shuttingDown.get()) {
            return;
        }
        log.warn("OpenD connection lost (errCode={}), initiating reconnect", errCode);
        requestBridge.failAll("Connection lost");
        scheduleReconnect();
    }

    // ---- Reconnect logic ----

    void scheduleReconnect() {
        if (shuttingDown.get()) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = calculateDelay(attempt);
        log.info("Scheduling reconnect attempt #{} in {}ms", attempt, delay);
        scheduler.schedule(this::reconnect, delay, TimeUnit.MILLISECONDS);
    }

    private long calculateDelay(int attempt) {
        FutuProperties.Reconnect rc = properties.getReconnect();
        long delay = (long) (rc.getInitialDelayMs() * Math.pow(rc.getMultiplier(), attempt - 1));
        return Math.min(delay, rc.getMaxDelayMs());
    }

    /**
     * Reconnects with proper resource cleanup to prevent memory leaks.
     * Closes old connections, releases references, then creates fresh ones.
     */
    void reconnect() {
        if (shuttingDown.get()) {
            return;
        }
        if (!reconnectLock.tryLock()) {
            log.debug("Reconnect already in progress, skipping");
            return;
        }
        try {
            log.info("Starting reconnect sequence...");

            // Step 1: Close old connections (releases socket + internal buffers)
            closeConnectionsQuietly();

            // Step 2: Clear all pending async requests (fail futures, clear map)
            requestBridge.failAll("Reconnecting");

            // Step 3: Create fresh connections
            connect();

        } catch (Exception e) {
            log.error("Reconnect failed: {}", e.getMessage(), e);
            scheduleReconnect();
        } finally {
            reconnectLock.unlock();
        }
    }

    /**
     * Periodic health check. If connection is not READY, triggers reconnect.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${futu.reconnect.max-delay-ms:60000}")
    public void healthCheck() {
        if (shuttingDown.get()) {
            return;
        }
        FTAPI_Conn_Qot conn = connQot;
        if (conn == null) {
            log.warn("Health check: no Qot connection, scheduling reconnect");
            scheduleReconnect();
            return;
        }
        ConnStatus status = conn.getConnStatus();
        if (status != ConnStatus.READY) {
            log.warn("Health check: connection status={}, scheduling reconnect", status);
            scheduleReconnect();
        }
    }

    // ---- Resource cleanup ----

    private void closeConnectionsQuietly() {
        closeQuietly(connQot, "Qot");
        closeQuietly(connTrd, "Trd");
        connQot = null;
        connTrd = null;
    }

    private void closeQuietly(AutoCloseable conn, String name) {
        if (conn != null) {
            try {
                conn.close();
                log.debug("Closed {} connection", name);
            } catch (Exception e) {
                log.warn("Error closing {} connection: {}", name, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        log.info("Shutting down Futu connection manager...");
        scheduler.shutdownNow();
        closeConnectionsQuietly();
        if (apiInitialized.get()) {
            FTAPI.unInit();
            apiInitialized.set(false);
        }
        log.info("Futu connection manager shut down");
    }

    // ---- Accessors ----

    /**
     * Returns the active Qot connection, or null if not connected.
     */
    public FTAPI_Conn_Qot getConnQot() {
        return connQot;
    }

    /**
     * Returns whether the connection is ready for requests.
     */
    public boolean isReady() {
        FTAPI_Conn_Qot conn = connQot;
        return conn != null && conn.getConnStatus() == ConnStatus.READY;
    }

    public void setPostReconnectHook(Runnable hook) {
        this.postReconnectHook = hook;
    }
}
