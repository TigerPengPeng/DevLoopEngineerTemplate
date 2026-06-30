package com.autotrading.startup;

import com.autotrading.account.StockGroupService;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.futu.FutuQuoteHandler;
import com.autotrading.market.KLineService;
import com.autotrading.market.MarketSessionService;
import com.autotrading.market.QuoteSubscriptionService;
import com.autotrading.market.SnapshotPollingService;
import com.autotrading.model.StockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the full startup sequence after Spring context is ready:
 * wait for connection -> fetch groups -> fetch K-lines -> register -> subscribe -> enable monitoring.
 * Also wires the post-reconnect hook and periodic tasks.
 */
@Component
public class ApplicationStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupRunner.class);

    private final FutuConnectionManager connectionManager;
    private final FutuQuoteHandler quoteHandler;
    private final StockGroupService stockGroupService;
    private final KLineService kLineService;
    private final QuoteSubscriptionService subscriptionService;
    private final MarketSessionService marketSessionService;
    private final QuoteProcessor quoteProcessor;
    private final SnapshotPollingService snapshotPollingService;

    private volatile List<StockInfo> monitoredStocks = List.of();
    private volatile boolean usingSnapshotFallback = false;

    public ApplicationStartupRunner(FutuConnectionManager connectionManager,
                                     FutuQuoteHandler quoteHandler,
                                     StockGroupService stockGroupService,
                                     KLineService kLineService,
                                     QuoteSubscriptionService subscriptionService,
                                     MarketSessionService marketSessionService,
                                     QuoteProcessor quoteProcessor,
                                     SnapshotPollingService snapshotPollingService) {
        this.connectionManager = connectionManager;
        this.quoteHandler = quoteHandler;
        this.stockGroupService = stockGroupService;
        this.kLineService = kLineService;
        this.subscriptionService = subscriptionService;
        this.marketSessionService = marketSessionService;
        this.quoteProcessor = quoteProcessor;
        this.snapshotPollingService = snapshotPollingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== Starting Futu Stock Monitor initialization ===");

        // Wire quote processor as the push listener
        quoteHandler.setQuoteListener(quoteProcessor);

        // Wire snapshot polling listener too (fallback price source)
        snapshotPollingService.setListener(quoteProcessor);

        // Wire post-reconnect hook
        connectionManager.setPostReconnectHook(this::onReconnected);

        // Wait for connection to be ready
        if (!waitForConnection()) {
            log.error("Failed to establish initial connection, will retry via health check");
            return;
        }

        reloadStocks();
    }

    private boolean waitForConnection() {
        for (int i = 0; i < 30; i++) {
            if (connectionManager.isReady()) {
                return true;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return connectionManager.isReady();
    }

    /**
     * Reloads the stock list from Futu OpenD. Called at startup and from /api/refresh-stocks.
     * Returns the number of stocks loaded.
     */
    public int reloadStocks() {
        try {
            // Step 1: Fetch stocks
            log.info("[1/4] Fetching stock groups...");
            monitoredStocks = stockGroupService.resolveTargetStocks();
            if (monitoredStocks.isEmpty()) {
                log.warn("No stocks to monitor. Add stocks to a Futu group and restart.");
                return 0;
            }
            log.info("[1/4] Found {} stocks to monitor", monitoredStocks.size());

            // Step 2: Fetch K-lines for MA calculation
            log.info("[2/4] Fetching daily K-lines...");
            kLineService.fetchAll(monitoredStocks);
            log.info("[2/4] Fetching weekly K-lines...");
            kLineService.fetchAll(monitoredStocks, "week");
            log.info("[2/4] K-line data cached for {} stocks", kLineService.cachedStockCount());

            // Step 3: Register stocks for market state + quote processing + snapshot polling
            log.info("[3/4] Registering stocks...");
            marketSessionService.registerStocks(monitoredStocks);
            quoteProcessor.registerStocks(monitoredStocks);
            snapshotPollingService.setStocks(monitoredStocks);
            marketSessionService.pollMarketState();

            // Step 4: Subscribe to real-time quotes
            log.info("[4/4] Subscribing to real-time quotes...");
            int subscribed = subscriptionService.subscribeAll(monitoredStocks);

            // Fallback: if push subscription failed, enable snapshot polling
            if (subscribed == 0) {
                log.warn("Push subscription failed (likely insufficient quote permission), "
                        + "enabling snapshot polling fallback");
                usingSnapshotFallback = true;
                snapshotPollingService.setPolling(true);
                // Do an immediate poll so prices show up right away
                snapshotPollingService.pollSnapshots();
            }

            // Enable event processing
            quoteProcessor.setMonitoring(true);
            log.info("=== Monitoring started for {} stocks ===", monitoredStocks.size());
            return monitoredStocks.size();
        } catch (Exception e) {
            log.error("Monitoring initialization failed: {}", e.getMessage(), e);
            return 0;        }
    }

    /**
     * Called after a successful reconnect.
     * Restores subscriptions and re-enables monitoring.
     */
    private void onReconnected() {
        log.info("Post-reconnect: restoring monitoring...");
        try {
            if (!monitoredStocks.isEmpty()) {
                if (usingSnapshotFallback) {
                    snapshotPollingService.setPolling(true);
                    snapshotPollingService.pollSnapshots();
                    log.info("Post-reconnect: snapshot polling re-enabled");
                } else {
                    int n = subscriptionService.resubscribeAll();
                    log.info("Post-reconnect: {} stocks resubscribed",
                            n);
                }
                quoteProcessor.setMonitoring(true);
            }
        } catch (Exception e) {
            log.error("Post-reconnect monitoring restore failed: {}", e.getMessage(), e);
        }
    }

    // ---- Periodic tasks ----

    @Scheduled(fixedDelayString = "${futu.monitor.market-state-poll-interval:30000}")
    public void pollMarketState() {
        if (connectionManager.isReady() && !monitoredStocks.isEmpty()) {
            marketSessionService.pollMarketState();
        }
    }

    @Scheduled(fixedDelayString = "${futu.monitor.kline-refresh-interval:60000}")
    public void refreshKLines() {
        if (connectionManager.isReady() && !monitoredStocks.isEmpty()) {
            log.debug("Refreshing K-line data (daily + weekly)...");
            kLineService.refreshAll(monitoredStocks);
            kLineService.fetchAll(monitoredStocks, "week");
        }
    }
}
