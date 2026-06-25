package com.autotrading.notification;

import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.PriceAlert;
import com.autotrading.model.TradingSession;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generates email subject and HTML body for alert events.
 */
public class NotificationTemplate {

    private static final SimpleDateFormat TS_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String GREEN = "#16a34a";
    private static final String RED = "#dc2626";

    private NotificationTemplate() {}

    // ---- MA Event ----

    public static String maEventSubject(MAEvent event) {
        String action = event.getDirection() == Direction.BREAK_UP ? "突破" : "跌破";
        return String.format("[告警] %s(%s) %s MA%d",
                event.getStockName(), event.getStockKey(), action, event.getMaPeriod());
    }

    public static String maEventBody(MAEvent event) {
        String color = event.getDirection() == Direction.BREAK_UP ? GREEN : RED;
        String action = event.getDirection().getLabel();
        String[] parts = event.getStockKey().split("\\.");
        String marketLabel = marketLabel(parts.length > 0 ? Integer.parseInt(parts[0]) : 0);

        return htmlWrap(String.format(
                "<h2 style=\"color:%s\">%s %s MA%d</h2>" +
                "<table style=\"border-collapse:collapse;width:100%%;font-size:14px\">" +
                row("股票", event.getStockName() + " (" + event.getStockKey() + ")") +
                colorRow("事件", action + " MA" + event.getMaPeriod(), color) +
                colorRow("当前价", formatPrice(event.getPrice()), color) +
                row("MA" + event.getMaPeriod(), formatPrice(event.getMaValue())) +
                row("交易时段", event.getSession().getLabel()) +
                row("市场", marketLabel) +
                row("时间", TS_FMT.format(new Date(event.getTimestamp()))) +
                "</table>", color));
    }

    // ---- Price Alert ----

    public static String priceAlertSubject(PriceAlert alert) {
        String direction = alert.getDirection() == Direction.UP ? "涨" : "跌";
        return String.format("[告警] %s(%s) 日内%s %.2f%%",
                alert.getStockName(), alert.getStockKey(), direction, Math.abs(alert.getChangePercent()));
    }

    public static String priceAlertBody(PriceAlert alert) {
        String color = alert.getDirection() == Direction.UP ? GREEN : RED;
        String direction = alert.getDirection().getLabel();
        String[] parts = alert.getStockKey().split("\\.");
        String marketLabel = marketLabel(parts.length > 0 ? Integer.parseInt(parts[0]) : 0);
        String changeStr = String.format("%s%.2f%%",
                alert.getChangePercent() > 0 ? "+" : "", alert.getChangePercent());

        return htmlWrap(String.format(
                "<h2 style=\"color:%s\">%s %s</h2>" +
                "<table style=\"border-collapse:collapse;width:100%%;font-size:14px\">" +
                row("股票", alert.getStockName() + " (" + alert.getStockKey() + ")") +
                row("当前价", formatPrice(alert.getPrice())) +
                row("前收盘", formatPrice(alert.getPreClose())) +
                colorRow("波动幅度", changeStr, color) +
                row("阈值", "±" + alert.getThreshold() + "%") +
                row("交易时段", alert.getSession().getLabel()) +
                row("市场", marketLabel) +
                row("时间", TS_FMT.format(new Date(alert.getTimestamp()))) +
                "</table>", color));
    }

    // ---- Helpers ----

    private static String htmlWrap(String body) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto\">" +
                body +
                "<hr style=\"border:none;border-top:1px solid #ddd;margin:20px 0\">" +
                "<p style=\"font-size:12px;color:#999\">Futu Stock Monitor 自动告警</p>" +
                "</div>";
    }

    private static String row(String label, String value) {
        return String.format(
                "<tr><td style=\"padding:8px 12px;border:1px solid #e5e7eb;background:#f9fafb;font-weight:bold;width:30%%\">%s</td>" +
                "<td style=\"padding:8px 12px;border:1px solid #e5e7eb\">%s</td></tr>",
                label, value);
    }

    private static String colorRow(String label, String value, String color) {
        return String.format(
                "<tr><td style=\"padding:8px 12px;border:1px solid #e5e7eb;background:#f9fafb;font-weight:bold\">%s</td>" +
                "<td style=\"padding:8px 12px;border:1px solid #e5e7eb;color:%s;font-weight:bold\">%s</td></tr>",
                label, color, value);
    }

    private static String formatPrice(double price) {
        return String.format("%.4f", price);
    }

    private static String marketLabel(int market) {
        if (market == 11) return "美股";
        if (market == 2) return "港股";
        if (market == 21) return "A股(沪)";
        if (market == 22) return "A股(深)";
        return "M" + market;
    }
}
