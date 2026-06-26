package com.autotrading.monitor;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Custom Logback appender that captures ERROR-level logs into an in-memory ring buffer.
 * Exposed via /api/error-logs for the dashboard error log viewer.
 */
public class ErrorLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_ENTRIES = 200;
    private static final LinkedList<ErrorLogEntry> entries = new LinkedList<>();

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel() != ch.qos.logback.classic.Level.ERROR) {
            return;
        }

        String stackTrace = "";
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy != null) {
            stackTrace = ThrowableProxyUtil.asString(proxy);
        }

        ErrorLogEntry entry = new ErrorLogEntry(
                Instant.ofEpochMilli(event.getTimeStamp()).toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                stackTrace
        );

        synchronized (entries) {
            entries.addFirst(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeLast();
            }
        }
    }

    public static List<ErrorLogEntry> getEntries(int limit) {
        synchronized (entries) {
            int size = Math.min(limit, entries.size());
            return new LinkedList<>(entries.subList(0, size));
        }
    }

    public static int getEntryCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public static void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    /**
     * Programmatic registration: attaches this appender to the root logger.
     */
    public static void register() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ErrorLogAppender appender = new ErrorLogAppender();
        appender.setContext(rootLogger.getLoggerContext());
        appender.setName("ERROR_CAPTURE");
        appender.start();
        rootLogger.addAppender(appender);
    }

    public record ErrorLogEntry(String timestamp, String logger, String thread,
                                 String message, String stackTrace) {}
}
