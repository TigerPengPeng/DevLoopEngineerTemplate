package com.autotrading.monitor;

import com.autotrading.market.SectorTrendReportService;
import com.autotrading.market.SectorTrendReportService.SectorTrendReport;
import com.autotrading.notification.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Daily sector trend report scheduler.
 * Generates a report every weekday at 08:00 Asia/Shanghai,
 * stores it in memory for history viewing, and sends it via email.
 */
@Component
public class SectorTrendReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SectorTrendReportScheduler.class);
    private static final int MAX_HISTORY = 30;

    private final SectorTrendReportService reportService;
    private final EmailNotificationService emailService;
    private final LinkedList<SectorTrendReport> history = new LinkedList<>();

    public SectorTrendReportScheduler(SectorTrendReportService reportService,
                                       EmailNotificationService emailService) {
        this.reportService = reportService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Shanghai")
    public void dailyReport() {
        generateAndSend(true);
    }

    /**
     * Manual trigger (from API). Returns the generated report.
     */
    public SectorTrendReport generateAndSend(boolean sendEmail) {
        log.info("Generating sector trend report...");
        SectorTrendReport report = reportService.generateReport();
        storeReport(report);

        if (sendEmail) {
            String subject = String.format("[行业趋势报告] %s - %d 个板块分析",
                    report.date(), report.sectors().size());
            try {
                emailService.sendSectorTrendReport(subject, report);
            } catch (Exception e) {
                log.error("Failed to send sector trend email: {}", e.getMessage(), e);
            }
        }

        log.info("Sector trend report generated: {} sectors, sentiment={}",
                report.sectors().size(), report.overallSentiment());
        return report;
    }

    private void storeReport(SectorTrendReport report) {
        synchronized (history) {
            history.addFirst(report);
            while (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
        }
    }

    public List<SectorTrendReport> getHistory() {
        synchronized (history) {
            return new LinkedList<>(history);
        }
    }

    public SectorTrendReport getLatest() {
        synchronized (history) {
            return history.isEmpty() ? null : history.getFirst();
        }
    }

    public SectorTrendReport getByDate(String date) {
        synchronized (history) {
            return history.stream()
                    .filter(r -> r.date().equals(date))
                    .findFirst()
                    .orElse(null);
        }
    }
}
