package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.indicator.MACalculator;
import com.autotrading.market.KLineService;
import com.autotrading.model.StockInfo;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.notification.NotificationTemplate;
import com.autotrading.startup.QuoteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled MA breakdown scanner.
 * Scans all monitored stocks for prices below any configured MA (5/13/30/55).
 * Aggregates results into a single batch email, filtered through the noise filter
 * so stocks already alerted recently are not re-emailed.
 * <p>
 * Default schedule: 10:00 and 14:00 and 22:00 Asia/Shanghai on weekdays.
 */
@Component
public class MABreakdownScanner {

    private static final Logger log = LoggerFactory.getLogger(MABreakdownScanner.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final KLineService kLineService;
    private final QuoteProcessor quoteProcessor;
    private final EmailNotificationService emailService;
    private final AlertNoiseFilter noiseFilter;
    private final List<Integer> maPeriods;

    public MABreakdownScanner(KLineService kLineService, QuoteProcessor quoteProcessor,
                               EmailNotificationService emailService,
                               AlertNoiseFilter noiseFilter,
                               FutuProperties properties) {
        this.kLineService = kLineService;
        this.quoteProcessor = quoteProcessor;
        this.emailService = emailService;
        this.noiseFilter = noiseFilter;
        this.maPeriods = properties.getMonitor().getMaPeriods();
    }

    @Scheduled(cron = "0 0 10,14 * * MON-FRI", zone = "Asia/Shanghai")
    public void scanMorning() {
        runScan("A股盘中");
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "Asia/Shanghai")
    public void scanOvernight() {
        runScan("美股盘中");
    }

    /**
     * Manual trigger (from API).
     */
    public List<NotificationTemplate.MABreakdownItem> runScan(String label) {
        log.info("MA breakdown scan triggered: {}", label);

        Map<String, QuoteProcessor.PriceSnapshot> prices = quoteProcessor.getLatestPrices();
        List<NotificationTemplate.MABreakdownItem> items = new ArrayList<>();

        for (StockInfo stock : quoteProcessor.getStocks()) {
            List<Double> closes = kLineService.getCloses(stock.key());
            if (closes.isEmpty()) continue;

            QuoteProcessor.PriceSnapshot snap = prices.get(stock.key());
            double currentPrice = snap != null ? snap.curPrice() : closes.get(closes.size() - 1);
            if (currentPrice <= 0) continue;

            Map<Integer, Double> maValues = MACalculator.calculateAll(closes, maPeriods);
            List<Integer> broken = new ArrayList<>();
            Map<Integer, Double> brokenMaValues = new HashMap<>();

            for (int period : maPeriods) {
                Double maVal = maValues.get(period);
                if (maVal != null && !Double.isNaN(maVal) && currentPrice < maVal) {
                    broken.add(period);
                    brokenMaValues.put(period, maVal);
                }
            }

            if (!broken.isEmpty()) {
                items.add(new NotificationTemplate.MABreakdownItem(
                        stock.key(), stock.getName(), currentPrice, broken, brokenMaValues));
            }
        }

        log.info("MA breakdown scan: {} stocks below at least one MA", items.size());

        // Filter items through the noise filter — only newly qualifying stocks get emailed
        long now = System.currentTimeMillis();
        List<NotificationTemplate.MABreakdownItem> toEmail = items.stream()
                .filter(item -> noiseFilter.shouldSendEmail("BREAKDOWN", item.stockKey(), now))
                .toList();

        if (toEmail.isEmpty()) {
            log.info("MA breakdown: {} stocks below MA but all suppressed by noise filter", items.size());
            return items;
        }

        String timeStr = LocalDateTime.now().format(TS_FMT);
        String subject = String.format("[MA破位扫描] %s %d 只股票跌破均线 (%s)",
                label, toEmail.size(), timeStr);

        try {
            emailService.sendMABreakdownReport(subject, timeStr, toEmail);
        } catch (Exception e) {
            log.error("Failed to send MA breakdown email: {}", e.getMessage(), e);
        }

        return items;
    }
}
