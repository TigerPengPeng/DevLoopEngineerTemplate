package com.autotrading.monitor;

import com.autotrading.market.TradingSignalService;
import com.autotrading.model.StockInfo;
import com.autotrading.notification.EmailHistoryService;
import com.autotrading.notification.EmailNotificationService;
import com.autotrading.entity.AlertRecord;
import com.autotrading.notification.NotificationTemplate;
import com.autotrading.repository.AlertRecordRepository;
import com.autotrading.startup.QuoteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically scans all monitored stocks for buy/sell signals.
 * When a NEW signal appears (not previously notified), it is recorded in
 * history and dispatched as an email.
 * <p>
 * A secondary noise filter suppresses signal emails for the same stock within
 * a configurable cooldown window, even if the signal key is technically new.
 *
 * Dedup key: stockKey + signal date + signal type + strategy.
 * Only the latest K-line bar's signal is considered "new" to avoid
 * re-alerting on historical signals from earlier bars.
 */
@Component
public class TradingSignalScanner {

    private static final Logger log = LoggerFactory.getLogger(TradingSignalScanner.class);
    private static final int MAX_RECORDS = 200;

    private final TradingSignalService signalService;
    private final QuoteProcessor quoteProcessor;
    private final EmailNotificationService emailService;
    private final EmailHistoryService emailHistoryService;
    private final AlertNoiseFilter noiseFilter;
    private final AlertRecordRepository alertRecordRepository;

    /** Tracks which signal dedup keys have already been notified. */
    private final Set<String> notifiedKeys = ConcurrentHashMap.newKeySet();

    /** False until the first scan; the first scan seeds known signals without emailing. */
    private volatile boolean initialized = false;

    /** Ring buffer of signal records for dashboard display. */
    private final List<SignalRecord> records = Collections.synchronizedList(new ArrayList<>());

    public TradingSignalScanner(TradingSignalService signalService,
                                 QuoteProcessor quoteProcessor,
                                 EmailNotificationService emailService,
                                 EmailHistoryService emailHistoryService,
                                 AlertNoiseFilter noiseFilter,
                                 AlertRecordRepository alertRecordRepository) {
        this.signalService = signalService;
        this.quoteProcessor = quoteProcessor;
        this.emailService = emailService;
        this.emailHistoryService = emailHistoryService;
        this.noiseFilter = noiseFilter;
        this.alertRecordRepository = alertRecordRepository;
    }

    @Scheduled(fixedDelayString = "${futu.monitor.signal-scan-interval-ms:90000}")
    public void scanSignals() {
        if (!quoteProcessor.isMonitoring()) {
            return;
        }

        List<StockInfo> stocks = quoteProcessor.getStocks();
        List<SignalRecord> newSignals = new ArrayList<>();
        // BF-3: On the first scan after startup, seed notifiedKeys with every
        // existing (historical) signal so historical signals are shown on the
        // dashboard but never emailed. Only signals that appear AFTER this
        // first scan are treated as real-time and emailed.
        if (!initialized) {
            initialized = true;
            List<SignalRecord> seedRecords = new ArrayList<>();
            for (StockInfo stock : stocks) {
                try {
                    List<TradingSignalService.Signal> signals = signalService.getSignals(
                            stock.getMarket(), stock.getCode());
                    for (TradingSignalService.Signal s : signals) {
                        notifiedKeys.add(stock.key() + ":" + s.date() + ":" + s.type() + ":" + s.strategy());
                    }
                    if (!signals.isEmpty()) {
                        TradingSignalService.Signal latest = signals.get(signals.size() - 1);
                        seedRecords.add(new SignalRecord(
                                stock.key(), stock.getName(), latest.type().name(),
                                latest.strategy(), latest.reason(), latest.price(),
                                latest.date(), System.currentTimeMillis()));
                    }
                } catch (Exception e) {
                    log.debug("Initial signal seed failed for {}: {}", stock.key(), e.getMessage());
                }
            }
            if (!seedRecords.isEmpty()) {
                synchronized (records) {
                    for (SignalRecord rec : seedRecords) {
                        records.add(0, rec);
                    }
                    while (records.size() > MAX_RECORDS) {
                        records.remove(records.size() - 1);
                    }
                }
            }
            log.info("First signal scan: seeded {} historical signal(s) for display only (no email)", seedRecords.size());
            return;
        }
        for (StockInfo stock : stocks) {
            try {
                List<TradingSignalService.Signal> signals = signalService.getSignals(
                        stock.getMarket(), stock.getCode());
                if (signals.isEmpty()) {
                    continue;
                }

                // Only check the most recent signal per stock per scan.
                // Earlier signals were already evaluated in previous scans.
                TradingSignalService.Signal latest = signals.get(signals.size() - 1);
                String dedupKey = stock.key() + ":" + latest.date() + ":" + latest.type() + ":" + latest.strategy();

                if (notifiedKeys.contains(dedupKey)) {
                    continue;
                }

                notifiedKeys.add(dedupKey);
                SignalRecord rec = new SignalRecord(
                        stock.key(), stock.getName(), latest.type().name(),
                        latest.strategy(), latest.reason(), latest.price(),
                        latest.date(), System.currentTimeMillis());
                newSignals.add(rec);

            } catch (Exception e) {
                log.debug("Signal scan failed for {}: {}", stock.key(), e.getMessage());
            }
        }

        if (newSignals.isEmpty()) {
            return;
        }

        // Record all new signals
        synchronized (records) {
            for (SignalRecord rec : newSignals) {
                records.add(0, rec);
            }
            while (records.size() > MAX_RECORDS) {
                records.remove(records.size() - 1);
            }
        }

        log.info("Trading signal scan: {} new signal(s) detected", newSignals.size());

        // Send email for each new signal, filtered through noise filter
        for (SignalRecord rec : newSignals) {
            try {
                String noiseKey = rec.stockKey() + ":" + rec.signalType();
                boolean shouldEmail = noiseFilter.shouldSendEmail("SIGNAL", noiseKey, System.currentTimeMillis());
                // NR-4: persist all new signals (suppressed or not)
                alertRecordRepository.save(new AlertRecord("SIGNAL", rec.stockKey(),
                        rec.stockName(), rec.signalType() + " " + rec.strategy(),
                        rec.price(), "", rec.timestamp(), !shouldEmail));
                if (!shouldEmail) {
                    log.debug("Signal email suppressed (noise filter): {}", noiseKey);
                    continue;
                }
                String subject = NotificationTemplate.signalSubject(rec);
                String body = NotificationTemplate.signalBody(rec);
                emailService.sendSignalAlert(subject, body);
            } catch (Exception e) {
                log.error("Failed to send signal email for {}: {}", rec.stockKey(), e.getMessage(), e);
            }
        }
    }

    public List<SignalRecord> getRecords() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public void resetAll() {
        notifiedKeys.clear();
        initialized = false;
        synchronized (records) {
            records.clear();
        }
    }

    /** DTO for dashboard display and API serialization. */
    public record SignalRecord(String stockKey, String stockName, String signalType,
                                String strategy, String reason, double price,
                                String signalDate, long timestamp) {}
}
