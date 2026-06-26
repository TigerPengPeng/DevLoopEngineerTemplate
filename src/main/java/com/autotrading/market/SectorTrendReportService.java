package com.autotrading.market;

import com.autotrading.config.FutuProperties;
import com.autotrading.indicator.MACalculator;
import com.autotrading.model.StockInfo;
import com.autotrading.startup.QuoteProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Analyzes per-sector trends by aggregating member stock performance, MA positions,
 * and price momentum to produce a trend direction and market sentiment summary.
 */
@Service
public class SectorTrendReportService {

    private static final Logger log = LoggerFactory.getLogger(SectorTrendReportService.class);

    private final SectorMappingService sectorMappingService;
    private final KLineService kLineService;
    private final QuoteProcessor quoteProcessor;
    private final List<Integer> maPeriods;

    public SectorTrendReportService(SectorMappingService sectorMappingService,
                                     KLineService kLineService,
                                     QuoteProcessor quoteProcessor,
                                     FutuProperties properties) {
        this.sectorMappingService = sectorMappingService;
        this.kLineService = kLineService;
        this.quoteProcessor = quoteProcessor;
        this.maPeriods = properties.getMonitor().getMaPeriods();
    }

    /**
     * Generates a full sector trend report for all monitored stocks.
     */
    public SectorTrendReport generateReport() {
        List<StockInfo> stocks = quoteProcessor.getStocks();
        Map<String, List<StockInfo>> grouped = sectorMappingService.groupBySector(stocks);
        Map<String, QuoteProcessor.PriceSnapshot> prices = quoteProcessor.getLatestPrices();

        List<SectorAnalysis> analyses = new ArrayList<>();
        for (Map.Entry<String, List<StockInfo>> entry : grouped.entrySet()) {
            SectorAnalysis analysis = analyzeSector(entry.getKey(), entry.getValue(), prices);
            analyses.add(analysis);
        }

        // Sort by bearish count descending (most at-risk sectors first)
        analyses.sort((a, b) -> Double.compare(b.riskScore, a.riskScore));

        String overallSentiment = computeOverallSentiment(analyses);

        return new SectorTrendReport(
                LocalDate.now().toString(),
                analyses,
                overallSentiment,
                System.currentTimeMillis()
        );
    }

    private SectorAnalysis analyzeSector(String sectorName, List<StockInfo> members,
                                          Map<String, QuoteProcessor.PriceSnapshot> prices) {
        int bullish = 0, bearish = 0, neutral = 0;
        double totalChange5d = 0;
        double totalChange20d = 0;
        int validCount = 0;
        List<SectorStockDetail> details = new ArrayList<>();

        for (StockInfo stock : members) {
            List<Double> closes = kLineService.getCloses(stock.key());
            QuoteProcessor.PriceSnapshot snap = prices.get(stock.key());
            double currentPrice = snap != null ? snap.curPrice() :
                    (!closes.isEmpty() ? closes.get(closes.size() - 1) : 0);

            if (currentPrice <= 0 || closes.isEmpty()) {
                neutral++;
                details.add(new SectorStockDetail(stock.key(), stock.getName(), currentPrice,
                        0, 0, "N/A", List.of()));
                continue;
            }

            validCount++;

            // 5-day and 20-day momentum
            double change5d = closes.size() >= 6
                    ? (currentPrice - closes.get(closes.size() - 6)) / closes.get(closes.size() - 6) * 100
                    : 0;
            double change20d = closes.size() >= 21
                    ? (currentPrice - closes.get(closes.size() - 21)) / closes.get(closes.size() - 21) * 100
                    : 0;
            totalChange5d += change5d;
            totalChange20d += change20d;

            // MA position analysis
            Map<Integer, Double> maValues = MACalculator.calculateAll(closes, maPeriods);
            List<Integer> aboveMA = new ArrayList<>();
            List<Integer> belowMA = new ArrayList<>();
            for (int period : maPeriods) {
                Double maVal = maValues.get(period);
                if (maVal != null && !Double.isNaN(maVal)) {
                    if (currentPrice >= maVal) {
                        aboveMA.add(period);
                    } else {
                        belowMA.add(period);
                    }
                }
            }

            // Trend: bullish if above more MAs than below
            String trend;
            if (aboveMA.size() > belowMA.size() && change5d >= 0) {
                trend = "看多";
                bullish++;
            } else if (belowMA.size() > aboveMA.size() && change5d < 0) {
                trend = "看空";
                bearish++;
            } else {
                trend = "中性";
                neutral++;
            }

            details.add(new SectorStockDetail(stock.key(), stock.getName(), currentPrice,
                    Math.round(change5d * 100.0) / 100.0,
                    Math.round(change20d * 100.0) / 100.0,
                    trend, belowMA));
        }

        double avgChange5d = validCount > 0 ? Math.round(totalChange5d / validCount * 100.0) / 100.0 : 0;
        double avgChange20d = validCount > 0 ? Math.round(totalChange20d / validCount * 100.0) / 100.0 : 0;

        // Sector sentiment
        String sentiment;
        double riskScore = 0;
        if (bearish > bullish) {
            sentiment = bearish > bullish + 1 ? "偏空" : "中性偏空";
            riskScore = (double) bearish / members.size() * 100;
        } else if (bullish > bearish) {
            sentiment = bullish > bearish + 1 ? "偏多" : "中性偏多";
            riskScore = (double) bearish / members.size() * 40;
        } else {
            sentiment = "中性";
            riskScore = 50;
        }

        return new SectorAnalysis(sectorName, members.size(), bullish, bearish, neutral,
                avgChange5d, avgChange20d, sentiment, Math.round(riskScore), details);
    }

    private String computeOverallSentiment(List<SectorAnalysis> analyses) {
        int totalBullish = analyses.stream().mapToInt(a -> a.bullishCount).sum();
        int totalBearish = analyses.stream().mapToInt(a -> a.bearishCount).sum();

        if (totalBullish > totalBearish * 1.3) {
            return "整体偏多 - 市场情绪积极，多个板块呈现上涨趋势";
        } else if (totalBearish > totalBullish * 1.3) {
            return "整体偏空 - 市场情绪谨慎，多个板块面临调整压力";
        } else {
            return "整体中性 - 市场分化，多空交织";
        }
    }

    // ---- DTOs ----

    public record SectorTrendReport(String date, List<SectorAnalysis> sectors,
                                     String overallSentiment, long generatedAt) {}

    public record SectorAnalysis(String sectorName, int memberCount,
                                  int bullishCount, int bearishCount, int neutralCount,
                                  double avgChange5d, double avgChange20d,
                                  String sentiment, double riskScore,
                                  List<SectorStockDetail> stocks) {}

    public record SectorStockDetail(String stockKey, String stockName,
                                     double currentPrice, double change5d, double change20d,
                                     String trend, List<Integer> belowMA) {}
}
