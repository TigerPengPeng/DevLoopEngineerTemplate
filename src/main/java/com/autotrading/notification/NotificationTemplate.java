package com.autotrading.notification;

import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generates email subject and HTML body for alert events.
 */
public class NotificationTemplate {

    /** DTO for MA breakdown scan report rows. */
    public record MABreakdownItem(String stockKey, String stockName, double currentPrice,
                                   java.util.List<Integer> brokenPeriods,
                                   java.util.Map<Integer, Double> maValues) {}

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

        // Use StringBuilder to avoid String.format treating row() outputs
        // (which contain literal % chars from width:30%) as format specifiers.
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 style=\"color:").append(color).append("\">")
          .append(action).append(" MA").append(event.getMaPeriod()).append("</h2>");
        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:14px\">");
        sb.append(row("股票", event.getStockName() + " (" + event.getStockKey() + ")"));
        sb.append(colorRow("事件", action + " MA" + event.getMaPeriod(), color));
        sb.append(colorRow("当前价", formatPrice(event.getPrice()), color));
        sb.append(row("MA" + event.getMaPeriod(), formatPrice(event.getMaValue())));
        sb.append(row("交易时段", event.getSession().getLabel()));
        sb.append(row("市场", marketLabel));
        sb.append(row("时间", TS_FMT.format(new Date(event.getTimestamp()))));
        sb.append("</table>");
        return htmlWrap(sb.toString());
    }

    // ---- Daily Risk Report ----

    public static String riskReportBody(String marketLabel, String dateStr,
                                         java.util.List<RiskReportItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 style=\"color:#dc2626\">")
          .append(marketLabel).append(" 当日风险股票汇总</h2>");
        sb.append("<p style=\"color:#8b949e;font-size:13px;margin-bottom:16px\">数据日期: ")
          .append(dateStr).append("</p>");

        if (items.isEmpty()) {
            sb.append("<p style=\"padding:20px;background:#f9fafb;border-radius:8px;text-align:center\">")
              .append("今日无高风险股票，市场整体平稳</p>");
        } else {
            sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
            sb.append("<tr><th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">股票</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">风险分</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">等级</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">涨跌%</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">风险因素</th></tr>");

            for (RiskReportItem item : items) {
                String levelColor = item.highRisk() ? RED : "#d29922";
                String levelText = item.highRisk() ? "高风险" : "中等";
                String changeColor = item.changeRate() >= 0 ? GREEN : RED;
                String changeStr = String.format("%s%.2f%%",
                        item.changeRate() >= 0 ? "+" : "", item.changeRate());

                sb.append("<tr>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-weight:600\">")
                  .append(item.stockName()).append(" <span style=\"color:#8b949e;font-size:12px\">")
                  .append(item.stockKey()).append("</span></td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;font-weight:700;color:")
                  .append(levelColor).append("\">").append(item.score()).append("</td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:")
                  .append(levelColor).append(";font-weight:600\">").append(levelText).append("</td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:")
                  .append(changeColor).append(";font-weight:600\">").append(changeStr).append("</td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-size:12px\">")
                  .append(String.join("、", item.riskFactors())).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        sb.append("<p style=\"margin-top:16px;font-size:12px;color:#8b949e\">")
          .append("风险分越高表示风险越大。60分以上为高风险，30-59为中等风险。")
          .append("</p>");

        return htmlWrap(sb.toString());
    }

    public record RiskReportItem(String stockKey, String stockName, int score, boolean highRisk,
                                 double changeRate, java.util.List<String> riskFactors) {}

    // ---- Helpers ----
    // ---- Fluctuation Batch Email ----

    public static String fluctuationBatchBody(String timeStr, String logic,
            java.util.List<com.autotrading.monitor.TimeWindowFluctuationMonitor.StockFluctuationResult> results) {
        StringBuilder sb = new StringBuilder();
        String logicLabel = "AND".equalsIgnoreCase(logic) ? "全部满足" : "任一满足";
        sb.append("<h2 style=\"color:#58a6ff\">").append("盘中波动汇总: ").append(results.size()).append(" 只股票触发规则")
          .append("</h2>");
        sb.append("<p style=\"color:#8b949e;font-size:13px;margin-bottom:16px\">")
          .append("时间: ").append(timeStr)
          .append(" | 规则逻辑: ").append(logicLabel).append("</p>");

        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
        sb.append("<tr><th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">股票</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">当前价</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">方向</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">匹配规则</th></tr>");

        for (var r : results) {
            String dirColor = "涨".equals(r.direction()) ? GREEN : RED;
            StringBuilder ruleDetails = new StringBuilder();
            for (var rm : r.allRules()) {
                if (ruleDetails.length() > 0) ruleDetails.append("<br>");
                String mark = rm.matched() ? "\u2714" : "\u2718";
                String color = rm.matched() ? GREEN : "#8b949e";
                ruleDetails.append("<span style=\"color:").append(color).append("\">")
                  .append(mark).append(" </span>")
                  .append(rm.rule().getWindowMinutes()).append("min >= ")
                  .append(String.format("%.1f", rm.rule().getThresholdPercent())).append("%")
                  .append(" (").append(String.format("%+.2f%%", rm.changePct())).append(")");
            }
            sb.append("<tr>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-weight:600\">")
              .append(r.stockName()).append(" <span style=\"color:#8b949e;font-size:12px\">")
              .append(r.stockKey()).append("</span></td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center\">")
              .append(formatPrice(r.currentPrice())).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:")
              .append(dirColor).append(";font-weight:600\">").append(r.direction()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-size:12px\">")
              .append(ruleDetails).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
        return htmlWrap(sb.toString());
    }

    // ---- MA Breakdown Scan Report ----

    public static String maBreakdownBody(String timeStr, java.util.List<MABreakdownItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 style=\"color:#dc2626\">MA 均线破位扫描</h2>");
        sb.append("<p style=\"color:#8b949e;font-size:13px;margin-bottom:16px\">时间: ").append(timeStr)
          .append(" | 扫描到 ").append(items.size()).append(" 只股票破位</p>");

        if (items.isEmpty()) {
            sb.append("<p style=\"padding:20px;background:#f9fafb;border-radius:8px;text-align:center\">")
              .append("当前无股票跌破任何 MA 均线</p>");
        } else {
            sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
            sb.append("<tr><th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">股票</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">当前价</th>")
              .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">跌破均线</th></tr>");

            for (MABreakdownItem item : items) {
                StringBuilder periods = new StringBuilder();
                for (int p : item.brokenPeriods()) {
                    if (periods.length() > 0) periods.append(" ");
                    Double maVal = item.maValues().get(p);
                    periods.append("<span style=\"display:inline-block;margin:2px 4px;padding:2px 8px;border-radius:10px;background:rgba(248,81,73,0.15);color:#dc2626;font-weight:600\">")
                      .append("MA").append(p)
                      .append(" (").append(formatPrice(maVal)).append(")</span>");
                }
                sb.append("<tr>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-weight:600\">")
                  .append(item.stockName()).append(" <span style=\"color:#8b949e;font-size:12px\">")
                  .append(item.stockKey()).append("</span></td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center\">")
                  .append(formatPrice(item.currentPrice())).append("</td>")
                  .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb\">")
                  .append(periods).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }
        return htmlWrap(sb.toString());
    }


    // ---- Sector Trend Report ----

    public static String sectorTrendBody(com.autotrading.market.SectorTrendReportService.SectorTrendReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 style=\"color:#58a6ff\">").append("行业趋势报告 - ").append(report.date()).append("</h2>");
        sb.append("<p style=\"padding:12px 16px;background:#1a1a2e;border-radius:8px;color:#e6edf3;font-size:14px;margin-bottom:16px\">")
          .append("<strong>市场总览: </strong>").append(report.overallSentiment()).append("</p>");

        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
        sb.append("<tr><th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">板块</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">成分</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">看多</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">看空</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">5日%</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">20日%</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">情绪</th>")
          .append("<th style=\"padding:8px 10px;border:1px solid #e5e7eb;background:#f3f4f6\">风险</th></tr>");

        for (var sector : report.sectors()) {
            String sentimentColor = getSentimentColor(sector.sentiment());
            String change5Color = sector.avgChange5d() >= 0 ? GREEN : RED;
            String change20Color = sector.avgChange20d() >= 0 ? GREEN : RED;
            String change5Str = String.format("%+.2f%%", sector.avgChange5d());
            String change20Str = String.format("%+.2f%%", sector.avgChange20d());
            String riskBar = String.format("%.0f", sector.riskScore());

            sb.append("<tr>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;font-weight:600\">").append(sector.sectorName()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center\">").append(sector.memberCount()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:").append(GREEN).append("\">").append(sector.bullishCount()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:").append(RED).append("\">").append(sector.bearishCount()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:").append(change5Color).append(";font-weight:600\">").append(change5Str).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:").append(change20Color).append(";font-weight:600\">").append(change20Str).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;color:").append(sentimentColor).append(";font-weight:600\">").append(sector.sentiment()).append("</td>")
              .append("<td style=\"padding:8px 10px;border:1px solid #e5e7eb;text-align:center;font-weight:700\">").append(riskBar).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");

        // Per-sector stock details (collapsible in email, visible inline)
        for (var sector : report.sectors()) {
            if (sector.stocks().isEmpty()) continue;
            sb.append("<h3 style=\"margin-top:16px;font-size:14px;color:#58a6ff\">")
              .append(sector.sectorName()).append(" - 成分股明细</h3>");
            sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:12px\">");
            sb.append("<tr><th style=\"padding:6px 8px;border:1px solid #e5e7eb;background:#f3f4f6;text-align:left\">股票</th>")
              .append("<th style=\"padding:6px 8px;border:1px solid #e5e7eb;background:#f3f4f6\">现价</th>")
              .append("<th style=\"padding:6px 8px;border:1px solid #e5e7eb;background:#f3f4f6\">5日%</th>")
              .append("<th style=\"padding:6px 8px;border:1px solid #e5e7eb;background:#f3f4f6\">趋势</th>")
              .append("<th style=\"padding:6px 8px;border:1px solid #e5e7eb;background:#f3f4f6\">跌破均线</th></tr>");

            for (var stock : sector.stocks()) {
                String trendColor = "看多".equals(stock.trend()) ? GREEN : "看空".equals(stock.trend()) ? RED : "#8b949e";
                String changeColor = stock.change5d() >= 0 ? GREEN : RED;
                String belowStr = stock.belowMA().isEmpty() ? "-" :
                        stock.belowMA().stream().map(p -> "MA" + p).reduce((a, b) -> a + " " + b).orElse("-");
                sb.append("<tr>")
                  .append("<td style=\"padding:6px 8px;border:1px solid #e5e7eb\">").append(stock.stockName())
                  .append(" <span style=\"color:#8b949e;font-size:11px\">").append(stock.stockKey()).append("</span></td>")
                  .append("<td style=\"padding:6px 8px;border:1px solid #e5e7eb;text-align:center\">").append(formatPrice(stock.currentPrice())).append("</td>")
                  .append("<td style=\"padding:6px 8px;border:1px solid #e5e7eb;text-align:center;color:").append(changeColor).append("\">")
                  .append(String.format("%+.2f%%", stock.change5d())).append("</td>")
                  .append("<td style=\"padding:6px 8px;border:1px solid #e5e7eb;text-align:center;color:").append(trendColor).append(";font-weight:600\">").append(stock.trend()).append("</td>")
                  .append("<td style=\"padding:6px 8px;border:1px solid #e5e7eb;text-align:center;color:").append(stock.belowMA().isEmpty() ? "#8b949e" : RED).append("\">").append(belowStr).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        return htmlWrap(sb.toString());
    }

    private static String getSentimentColor(String sentiment) {
        if (sentiment.contains("多")) return GREEN;
        if (sentiment.contains("空")) return RED;
        return "#d29922";
    }


    private static String htmlWrap(String body) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto\">" +
                body +
                "<hr style=\"border:none;border-top:1px solid #ddd;margin:20px 0\">" +
                "<p style=\"font-size:12px;color:#999\">Futu Stock Monitor 自动告警</p>" +
                "</div>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:8px 12px;border:1px solid #e5e7eb;background:#f9fafb;font-weight:bold;width:30%\">" + label + "</td>" +
                "<td style=\"padding:8px 12px;border:1px solid #e5e7eb\">" + value + "</td></tr>";
    }

    private static String colorRow(String label, String value, String color) {
        return "<tr><td style=\"padding:8px 12px;border:1px solid #e5e7eb;background:#f9fafb;font-weight:bold\">" + label + "</td>" +
                "<td style=\"padding:8px 12px;border:1px solid #e5e7eb;color:" + color + ";font-weight:bold\">" + value + "</td></tr>";
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
