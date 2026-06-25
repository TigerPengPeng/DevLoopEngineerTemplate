package com.autotrading.monitor;

import com.autotrading.market.RiskAssessmentService;
import com.autotrading.model.StockInfo;
import com.autotrading.notification.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled task that sends a daily risk report email after each market closes.
 *
 * A-share report: fires at 15:30 Asia/Shanghai (30 min after 15:00 close).
 * US stock report: fires at 17:00 America/New_York (1 hour after 16:00 close).
 *
 * Only stocks from the monitored list belonging to that market are included.
 */
@Component
public class DailyRiskReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyRiskReportScheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RiskAssessmentService riskService;
    private final EmailNotificationService emailService;
    private final com.autotrading.startup.QuoteProcessor quoteProcessor;

    public DailyRiskReportScheduler(RiskAssessmentService riskService,
                                     EmailNotificationService emailService,
                                     com.autotrading.startup.QuoteProcessor quoteProcessor) {
        this.riskService = riskService;
        this.emailService = emailService;
        this.quoteProcessor = quoteProcessor;
    }

    /**
     * A-share daily risk report at 15:30 Beijing time on weekdays.
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void aShareRiskReport() {
        sendReport("A股", StockInfo.MARKET_CN_SH, StockInfo.MARKET_CN_SZ);
    }

    /**
     * US stock daily risk report at 17:00 New York time on weekdays.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    public void usStockRiskReport() {
        sendReport("美股", StockInfo.MARKET_US);
    }

    private void sendReport(String marketLabel, int... markets) {
        log.info("Generating daily risk report for {}", marketLabel);

        List<StockInfo> allStocks = quoteProcessor.getStocks();
        List<StockInfo> targetStocks = new ArrayList<>();
        for (StockInfo s : allStocks) {
            for (int m : markets) {
                if (s.getMarket() == m) {
                    targetStocks.add(s);
                    break;
                }
            }
        }

        if (targetStocks.isEmpty()) {
            log.info("No {} stocks in monitored list, skipping risk report", marketLabel);
            return;
        }

        List<RiskAssessmentService.RiskAssessment> assessments = riskService.assessAll(targetStocks);

        // Only include medium and high risk
        List<RiskAssessmentService.RiskAssessment> risky = assessments.stream()
                .filter(a -> a.level() != RiskAssessmentService.RiskLevel.LOW)
                .toList();

        String dateStr = LocalDate.now().format(DATE_FMT);
        String subject = String.format("[%s风险报告] %s 当日风险股票汇总 (%d只)",
                marketLabel, dateStr, risky.size());

        log.info("{} risk report: {} stocks assessed, {} at risk", marketLabel, assessments.size(), risky.size());

        try {
            emailService.sendRiskReport(subject, marketLabel, dateStr, risky);
        } catch (Exception e) {
            log.error("Failed to send {} risk report email: {}", marketLabel, e.getMessage(), e);
        }
    }
}
