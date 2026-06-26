package com.autotrading.web;

import com.autotrading.config.FutuProperties;
import com.autotrading.futu.FutuConnectionManager;
import com.autotrading.market.KLineService;
import com.autotrading.market.MarketSessionService;
import com.autotrading.market.SnapshotPollingService;
import com.autotrading.model.StockInfo;
import com.autotrading.startup.QuoteProcessor;
import com.autotrading.monitor.AlertCoordinator;
import com.autotrading.monitor.TradingSignalScanner;
import com.autotrading.notification.EmailHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves a JSON status snapshot at /api/status for the dashboard page.
 * The HTML dashboard at / fetches this endpoint and renders it.
 */
@RestController
public class StatusController {

    private final FutuConnectionManager connectionManager;
    private final FutuProperties properties;
    private final QuoteProcessor quoteProcessor;
    private final KLineService kLineService;
    private final MarketSessionService marketSessionService;
    private final AlertCoordinator alertCoordinator;
    private final EmailHistoryService emailHistoryService;
    private final TradingSignalScanner signalScanner;
    private final SnapshotPollingService snapshotPollingService;

    public StatusController(FutuConnectionManager connectionManager,
                            FutuProperties properties,
                            QuoteProcessor quoteProcessor,
                            KLineService kLineService,
                            MarketSessionService marketSessionService,
                            AlertCoordinator alertCoordinator,
                            EmailHistoryService emailHistoryService,
                            TradingSignalScanner signalScanner,
                            SnapshotPollingService snapshotPollingService) {
        this.connectionManager = connectionManager;
        this.properties = properties;
        this.quoteProcessor = quoteProcessor;
        this.kLineService = kLineService;
        this.marketSessionService = marketSessionService;
        this.alertCoordinator = alertCoordinator;
        this.emailHistoryService = emailHistoryService;
        this.signalScanner = signalScanner;
        this.snapshotPollingService = snapshotPollingService;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", Instant.now().toString());

        // Connection
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("ready", connectionManager.isReady());
        conn.put("opendIp", properties.getOpend().getIp());
        conn.put("opendPort", properties.getOpend().getPort());
        root.put("connection", conn);

        // Monitoring
        Map<String, Object> mon = new LinkedHashMap<>();
        mon.put("enabled", quoteProcessor.isMonitoring());
        mon.put("stockCount", quoteProcessor.getRegisteredCount());
        mon.put("klineCachedCount", kLineService.cachedStockCount());
        root.put("monitoring", mon);

        // Config
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("maPeriods", properties.getMonitor().getMaPeriods());
        cfg.put("priceChangeThreshold", properties.getMonitor().getPriceChangeThreshold());
        cfg.put("alertCooldownMinutes", properties.getMonitor().getAlertCooldownMinutes());
        String group = properties.getFilter().getGroupName();
        cfg.put("groupName", group != null && !group.isBlank() ? group : "(first group)");
        root.put("config", cfg);

        // Stocks with latest prices
        List<Map<String, Object>> stocks = new ArrayList<>();
        Map<String, QuoteProcessor.PriceSnapshot> prices = quoteProcessor.getLatestPrices();
        for (StockInfo stock : quoteProcessor.getStocks()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("key", stock.key());
            s.put("code", stock.getCode());
            s.put("name", stock.getName());
            s.put("market", marketName(stock.getMarket()));
            QuoteProcessor.PriceSnapshot snap = prices.get(stock.key());
            if (snap != null) {
                s.put("price", snap.curPrice());
                s.put("preClose", snap.preClose());
                double pct = snap.preClose() > 0
                        ? (snap.curPrice() - snap.preClose()) / snap.preClose() * 100 : 0;
                s.put("changePercent", Math.round(pct * 100.0) / 100.0);
                s.put("priceTime", snap.updateTime());
            }
            s.put("session", marketSessionService.getSession(stock.getMarket()).getLabel());
            stocks.add(s);
        }
        stocks.sort((a, b) -> String.valueOf(a.get("code")).compareTo(String.valueOf(b.get("code"))));
        root.put("stocks", stocks);

        // Recent alerts
        root.put("recentAlerts", alertCoordinator.getRecentAlerts());
        root.put("emailHistory", emailHistoryService.getRecords());
        root.put("signalRecords", signalScanner.getRecords());

        return root;
    }

    @PostMapping("/api/refresh-prices")
    public Map<String, Object> refreshPrices() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            snapshotPollingService.pollSnapshots();
            result.put("status", "ok");
            result.put("message", "Price refresh triggered");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    private String marketName(int market) {
        return switch (market) {
            case 11 -> "US";
            case 2 -> "HK";
            case 21 -> "SH";
            case 22 -> "SZ";
            default -> "M" + market;
        };
    }
}
