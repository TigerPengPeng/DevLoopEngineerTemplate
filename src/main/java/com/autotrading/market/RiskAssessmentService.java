package com.autotrading.market;

import com.autotrading.indicator.MACalculator;
import com.autotrading.model.StockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Assesses risk for a stock by combining multiple technical indicators into a
 * single risk score (0-100). Higher score = higher risk.
 *
 * Risk factors:
 *  - Price position vs MA5/13/30/55 (below key MAs = bearish)
 *  - MA alignment (short MA below long MA = downtrend)
 *  - MACD (death cross / DIF below zero)
 *  - RSI extremes (overbought reversal risk)
 *  - Bollinger Band position
 *  - KDJ high-zone death cross
 *  - Volume-price divergence (heavy volume drop)
 */
@Service
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final KLineService kLineService;

    public RiskAssessmentService(KLineService kLineService) {
        this.kLineService = kLineService;
    }

    public enum RiskLevel { HIGH, MEDIUM, LOW }

    public record RiskAssessment(
            String stockKey,
            String stockName,
            int score,
            RiskLevel level,
            double currentPrice,
            double changeRate,
            List<String> riskFactors,
            List<String> positiveFactors
    ) {}

    /**
     * Assesses risk for a single stock.
     */
    public RiskAssessment assess(StockInfo stock) {
        List<KLineService.KLineData> klines = kLineService.getKLines(stock.getMarket(), stock.getCode());
        if (klines.isEmpty() || klines.size() < 30) {
            return new RiskAssessment(stock.key(), stock.getName(), 0, RiskLevel.LOW,
                    0, 0, List.of("数据不足"), List.of());
        }

        List<Double> closes = klines.stream().map(KLineService.KLineData::close).toList();
        List<Long> volumes = klines.stream().map(KLineService.KLineData::volume).toList();
        int last = closes.size() - 1;
        double price = closes.get(last);

        List<String> riskFactors = new ArrayList<>();
        List<String> positiveFactors = new ArrayList<>();
        int score = 0;

        // 1. MA position (below key MAs = bearish)
        double ma5 = MACalculator.calculateMA(closes, 5);
        double ma13 = MACalculator.calculateMA(closes, 13);
        double ma30 = MACalculator.calculateMA(closes, 30);
        double ma55 = MACalculator.calculateMA(closes, 55);

        if (!Double.isNaN(ma5) && price < ma5) { score += 10; riskFactors.add("跌破MA5"); }
        else if (!Double.isNaN(ma5)) { positiveFactors.add("站上MA5"); }

        if (!Double.isNaN(ma13) && price < ma13) { score += 15; riskFactors.add("跌破MA13"); }
        else if (!Double.isNaN(ma13)) { positiveFactors.add("站上MA13"); }

        if (!Double.isNaN(ma30) && price < ma30) { score += 15; riskFactors.add("跌破MA30"); }
        else if (!Double.isNaN(ma30)) { positiveFactors.add("站上MA30"); }

        if (!Double.isNaN(ma55) && price < ma55) { score += 20; riskFactors.add("跌破MA55(长期趋势线)"); }

        // 2. MA alignment (bearish: short below long)
        if (!Double.isNaN(ma5) && !Double.isNaN(ma13) && ma5 < ma13) {
            score += 10; riskFactors.add("MA5 < MA13 短期空头排列");
        }
        if (!Double.isNaN(ma13) && !Double.isNaN(ma30) && ma13 < ma30) {
            score += 10; riskFactors.add("MA13 < MA30 中期空头排列");
        }
        if (!Double.isNaN(ma30) && !Double.isNaN(ma55) && ma30 < ma55) {
            score += 5; riskFactors.add("MA30 < MA55 长期空头排列");
        }

        // 3. MACD
        if (closes.size() >= 35) {
            double[] ema12 = calcEMA(closes, 12);
            double[] ema26 = calcEMA(closes, 26);
            double[] dif = new double[closes.size()];
            for (int i = 0; i < closes.size(); i++) dif[i] = ema12[i] - ema26[i];
            double[] dea = calcEMAFromArray(dif, 9);

            if (dif[last] < dea[last]) { score += 10; riskFactors.add("MACD死叉"); }
            else { positiveFactors.add("MACD金叉"); }

            if (dif[last] < 0) { score += 10; riskFactors.add("MACD DIF在零轴下方"); }
        }

        // 4. RSI
        if (closes.size() >= 15) {
            double rsi = calcRSI(closes, 14);
            if (rsi > 70) { score += 15; riskFactors.add("RSI超买(" + round(rsi) + ") 可能回调"); }
            else if (rsi < 30) { score += 5; riskFactors.add("RSI超卖(" + round(rsi) + ")"); }
            else { positiveFactors.add("RSI中性(" + round(rsi) + ")"); }
        }

        // 5. Bollinger
        if (closes.size() >= 20) {
            double[] boll = calcBollinger(closes, 20);
            if (price < boll[2]) { score += 15; riskFactors.add("跌破布林带下轨"); }
            else if (price > boll[0]) { score += 10; riskFactors.add("突破布林带上轨 超买风险"); }
            else { positiveFactors.add("布林带中轨附近"); }
        }

        // 6. KDJ
        if (klines.size() >= 9) {
            double[] kdj = calcKDJ(klines, 9);
            if (kdj[0] < kdj[1] && kdj[0] > 80) {
                score += 10; riskFactors.add("KDJ高位死叉 注意风险");
            } else if (kdj[0] > kdj[1] && kdj[0] < 30) {
                positiveFactors.add("KDJ低位金叉 可能反弹");
            }
        }

        // 7. Volume-price: heavy volume + price drop
        if (volumes.size() >= 20) {
            long curVol = volumes.get(last);
            double avgVol = volumes.subList(volumes.size() - 21, volumes.size() - 1).stream()
                    .mapToLong(Long::longValue).average().orElse(0);
            double ratio = avgVol > 0 ? curVol / avgVol : 0;
            boolean priceDown = last > 0 && closes.get(last) < closes.get(last - 1);
            if (ratio > 2.0 && priceDown) {
                score += 15; riskFactors.add("放量大跌 量比" + round(ratio));
            }
        }

        // 8. Today's change rate
        double changeRate = klines.get(last).changeRate();
        if (changeRate < -3) { score += 15; riskFactors.add("大跌" + round(changeRate) + "%"); }
        else if (changeRate > 5) { score += 5; riskFactors.add("大涨" + round(changeRate) + "% 注意获利了结"); }

        // Normalize to 0-100
        score = Math.min(score, 100);

        RiskLevel level;
        if (score >= 60) level = RiskLevel.HIGH;
        else if (score >= 30) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        return new RiskAssessment(stock.key(), stock.getName(), score, level,
                round(price), changeRate, riskFactors, positiveFactors);
    }

    /**
     * Assesses risk for all stocks in a list, returning only those above a threshold.
     */
    public List<RiskAssessment> assessAll(List<StockInfo> stocks) {
        List<RiskAssessment> results = new ArrayList<>();
        for (StockInfo stock : stocks) {
            try {
                RiskAssessment ra = assess(stock);
                if (ra.level() != RiskLevel.LOW || !ra.riskFactors().isEmpty()) {
                    results.add(ra);
                }
            } catch (Exception e) {
                log.warn("Risk assessment failed for {}: {}", stock.key(), e.getMessage());
            }
        }
        results.sort(Comparator.comparingInt(RiskAssessment::score).reversed());
        return results;
    }

    // --- Helpers (same logic as StockAnalysisService, kept local for independence) ---

    private double[] calcEMA(List<Double> values, int period) {
        double[] ema = new double[values.size()];
        double mult = 2.0 / (period + 1);
        ema[0] = values.get(0);
        for (int i = 1; i < values.size(); i++) ema[i] = values.get(i) * mult + ema[i - 1] * (1 - mult);
        return ema;
    }

    private double[] calcEMAFromArray(double[] values, int period) {
        double[] ema = new double[values.length];
        double mult = 2.0 / (period + 1);
        ema[0] = values[0];
        for (int i = 1; i < values.length; i++) ema[i] = values[i] * mult + ema[i - 1] * (1 - mult);
        return ema;
    }

    private double calcRSI(List<Double> closes, int period) {
        double gain = 0, loss = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) gain += change; else loss -= change;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - 100 / (1 + rs);
    }

    private double[] calcBollinger(List<Double> closes, int period) {
        double sum = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) sum += closes.get(i);
        double sma = sum / period;
        double sqSum = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) sqSum += Math.pow(closes.get(i) - sma, 2);
        double sd = Math.sqrt(sqSum / period);
        return new double[]{ sma + 2 * sd, sma, sma - 2 * sd };
    }

    private double[] calcKDJ(List<KLineService.KLineData> klines, int n) {
        double prevK = 50, prevD = 50;
        for (int i = n - 1; i < klines.size(); i++) {
            double hh = Double.MIN_VALUE, ll = Double.MAX_VALUE;
            for (int j = i - n + 1; j <= i; j++) {
                hh = Math.max(hh, klines.get(j).high());
                ll = Math.min(ll, klines.get(j).low());
            }
            double rsv = hh == ll ? 0 : (klines.get(i).close() - ll) / (hh - ll) * 100;
            prevK = 2.0 / 3 * prevK + 1.0 / 3 * rsv;
            prevD = 2.0 / 3 * prevD + 1.0 / 3 * prevK;
        }
        double j = 3 * prevK - 2 * prevD;
        return new double[]{ prevK, prevD, j };
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
