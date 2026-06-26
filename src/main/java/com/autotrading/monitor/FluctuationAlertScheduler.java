package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.market.MarketSessionService;
import com.autotrading.model.StockInfo;
import com.autotrading.model.TradingSession;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.startup.QuoteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodically evaluates time-window fluctuation rules for all monitored stocks.
 * Qualifying stocks are aggregated into a single batch email.
 */
@Component
public class FluctuationAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(FluctuationAlertScheduler.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TimeWindowFluctuationMonitor monitor;
    private final QuoteProcessor quoteProcessor;
    private final MarketSessionService sessionService;
    private final EmailNotificationService emailService;

    public FluctuationAlertScheduler(TimeWindowFluctuationMonitor monitor,
                                      QuoteProcessor quoteProcessor,
                                      MarketSessionService sessionService,
                                      EmailNotificationService emailService) {
        this.monitor = monitor;
        this.quoteProcessor = quoteProcessor;
        this.sessionService = sessionService;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${futu.fluctuation.eval-interval-ms:30000}")
    public void evaluateAndAlert() {
        if (!quoteProcessor.isMonitoring()) {
            return;
        }

        List<StockInfo> stocks = quoteProcessor.getStocks();
        List<TimeWindowFluctuationMonitor.StockFluctuationResult> qualifying = new ArrayList<>();

        for (StockInfo stock : stocks) {
            TradingSession session = sessionService.getSession(stock.getMarket());
            if (!session.isTrading()) {
                continue;
            }
            TimeWindowFluctuationMonitor.StockFluctuationResult result =
                    monitor.evaluate(stock.key(), stock.getName());
            if (result != null) {
                qualifying.add(result);
            }
        }

        if (qualifying.isEmpty()) {
            return;
        }

        log.info("Fluctuation batch: {} stocks qualified for alert", qualifying.size());

        String timeStr = LocalDateTime.now().format(TS_FMT);
        FutuProperties.Fluctuation cfg = monitor.getConfig();
        String subject = String.format("[盘中波动汇总] %d 只股票触发波动规则 (%s)",
                qualifying.size(), timeStr);

        try {
            emailService.sendFluctuationBatch(subject, timeStr, cfg.getLogic(), qualifying);
        } catch (Exception e) {
            log.error("Failed to send fluctuation batch email: {}", e.getMessage(), e);
        }
    }
}
