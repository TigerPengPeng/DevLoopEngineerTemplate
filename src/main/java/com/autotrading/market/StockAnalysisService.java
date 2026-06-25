package com.autotrading.market;

import com.autotrading.indicator.MACalculator;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Technical analysis engine supporting multiple strategies.
 * Each strategy analyzes the K-line data and returns signals + indicators.
 */
@Service
public class StockAnalysisService {

    private final KLineService kLineService;

    public StockAnalysisService(KLineService kLineService) {
        this.kLineService = kLineService;
    }

    public List<String> getAvailableStrategies() {
        return List.of("ma_crossover", "macd", "rsi", "bollinger", "kdj", "volume");
    }

    public Map<String, Object> analyze(int market, String code, String strategy) {
        List<KLineService.KLineData> klines = kLineService.getKLines(market, code);
        if (klines.isEmpty()) {
            return Map.of("error", "No K-line data available", "strategy", strategy);
        }

        List<Double> closes = klines.stream().map(KLineService.KLineData::close).toList();
        List<Long> volumes = klines.stream().map(KLineService.KLineData::volume).toList();

        return switch (strategy) {
            case "ma_crossover" -> analyzeMACrossover(klines, closes);
            case "macd" -> analyzeMACD(closes);
            case "rsi" -> analyzeRSI(closes);
            case "bollinger" -> analyzeBollinger(closes);
            case "kdj" -> analyzeKDJ(klines);
            case "volume" -> analyzeVolume(klines, volumes);
            default -> analyzeMACrossover(klines, closes);
        };
    }

    private Map<String, Object> analyzeMACrossover(List<KLineService.KLineData> klines, List<Double> closes) {
        Map<String, Object> result = new LinkedHashMap<>();
        double curPrice = closes.get(closes.size() - 1);

        List<Integer> periods = List.of(5, 13, 30, 55);
        List<Map<String, Object>> maList = new ArrayList<>();
        List<Map<String, Object>> breakouts = new ArrayList<>();

        for (int period : periods) {
            double ma = MACalculator.calculateMA(closes, period);
            if (Double.isNaN(ma)) continue;

            Map<String, Object> maInfo = new LinkedHashMap<>();
            maInfo.put("period", period);
            maInfo.put("value", Math.round(ma * 100) / 100.0);
            maInfo.put("above", curPrice > ma);
            double distPct = (curPrice - ma) / ma * 100;
            maInfo.put("distancePercent", Math.round(distPct * 100) / 100.0);
            maList.add(maInfo);

            if (curPrice < ma) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("ma", "MA" + period);
                alert.put("type", "跌破");
                alert.put("maValue", Math.round(ma * 100) / 100.0);
                alert.put("price", Math.round(curPrice * 100) / 100.0);
                alert.put("distancePercent", Math.round(distPct * 100) / 100.0);
                breakouts.add(alert);
            }
        }

        result.put("strategy", "MA均线交叉");
        result.put("currentPrice", Math.round(curPrice * 100) / 100.0);
        result.put("maLines", maList);
        result.put("breakouts", breakouts);

        String signal;
        if (breakouts.size() >= 3) signal = "强烈看空 - 股价跌破多条均线";
        else if (breakouts.size() >= 2) signal = "看空 - 股价跌破关键均线";
        else if (breakouts.size() == 1) signal = "偏空 - 股价跌破MA" + periods;
        else signal = "多头排列 - 股价站上所有均线";
        result.put("signal", signal);
        result.put("signalType", breakouts.isEmpty() ? "bullish" : "bearish");
        return result;
    }

    private Map<String, Object> analyzeMACD(List<Double> closes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (closes.size() < 35) { result.put("error", "Insufficient data for MACD"); return result; }

        double[] ema12 = calcEMA(closes, 12);
        double[] ema26 = calcEMA(closes, 26);
        double[] dif = new double[closes.size()];
        for (int i = 0; i < closes.size(); i++) dif[i] = ema12[i] - ema26[i];

        double[] dea = calcEMAFromArray(dif, 9);
        double macd = (dif[closes.size()-1] - dea[closes.size()-1]) * 2;

        String signal;
        if (dif[closes.size()-1] > dea[closes.size()-1] && dif[closes.size()-1] > 0) signal = "金叉 + 零轴上方 - 强烈多头";
        else if (dif[closes.size()-1] > dea[closes.size()-1]) signal = "金叉 - 多头信号";
        else if (dif[closes.size()-1] < dea[closes.size()-1] && dif[closes.size()-1] < 0) signal = "死叉 + 零轴下方 - 强烈空头";
        else signal = "死叉 - 空头信号";

        result.put("strategy", "MACD");
        result.put("dif", Math.round(dif[closes.size()-1] * 100) / 100.0);
        result.put("dea", Math.round(dea[closes.size()-1] * 100) / 100.0);
        result.put("macd", Math.round(macd * 100) / 100.0);
        result.put("signal", signal);
        result.put("signalType", dif[closes.size()-1] > dea[closes.size()-1] ? "bullish" : "bearish");
        return result;
    }

    private Map<String, Object> analyzeRSI(List<Double> closes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (closes.size() < 15) { result.put("error", "Insufficient data for RSI"); return result; }

        double rsi = calcRSI(closes, 14);
        String signal;
        if (rsi > 80) signal = "超买 - 可能回调";
        else if (rsi > 70) signal = "偏强 - 接近超买区";
        else if (rsi < 20) signal = "超卖 - 可能反弹";
        else if (rsi < 30) signal = "偏弱 - 接近超卖区";
        else signal = "中性";

        result.put("strategy", "RSI(14)");
        result.put("rsi", Math.round(rsi * 100) / 100.0);
        result.put("signal", signal);
        result.put("signalType", rsi > 70 ? "bearish" : rsi < 30 ? "bullish" : "neutral");
        return result;
    }

    private Map<String, Object> analyzeBollinger(List<Double> closes) {
        Map<String, Object> result = new LinkedHashMap<>();
        int period = 20;
        if (closes.size() < period) { result.put("error", "Insufficient data for Bollinger"); return result; }

        double[] sma = new double[closes.size()];
        double[] upper = new double[closes.size()];
        double[] lower = new double[closes.size()];
        for (int i = period - 1; i < closes.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += closes.get(j);
            sma[i] = sum / period;
            double sqSum = 0;
            for (int j = i - period + 1; j <= i; j++) sqSum += Math.pow(closes.get(j) - sma[i], 2);
            double sd = Math.sqrt(sqSum / period);
            upper[i] = sma[i] + 2 * sd;
            lower[i] = sma[i] - 2 * sd;
        }

        int last = closes.size() - 1;
        double price = closes.get(last);
        String signal;
        if (price > upper[last]) signal = "突破上轨 - 强势突破或超买";
        else if (price < lower[last]) signal = "跌破下轨 - 超卖或加速下跌";
        else if (price > sma[last]) signal = "中轨上方 - 多头趋势";
        else signal = "中轨下方 - 空头趋势";

        result.put("strategy", "布林带(20,2)");
        result.put("upper", Math.round(upper[last] * 100) / 100.0);
        result.put("middle", Math.round(sma[last] * 100) / 100.0);
        result.put("lower", Math.round(lower[last] * 100) / 100.0);
        result.put("signal", signal);
        result.put("signalType", price > upper[last] ? "bearish" : price < lower[last] ? "bullish" : price > sma[last] ? "bullish" : "bearish");
        return result;
    }

    private Map<String, Object> analyzeKDJ(List<KLineService.KLineData> klines) {
        Map<String, Object> result = new LinkedHashMap<>();
        int n = 9;
        if (klines.size() < n) { result.put("error", "Insufficient data for KDJ"); return result; }

        double prevK = 50, prevD = 50, prevJ = 50;
        for (int i = n - 1; i < klines.size(); i++) {
            double hh = Double.MIN_VALUE, ll = Double.MAX_VALUE;
            for (int j = i - n + 1; j <= i; j++) {
                hh = Math.max(hh, klines.get(j).high());
                ll = Math.min(ll, klines.get(j).low());
            }
            double rsv = hh == ll ? 0 : (klines.get(i).close() - ll) / (hh - ll) * 100;
            prevK = 2.0/3 * prevK + 1.0/3 * rsv;
            prevD = 2.0/3 * prevD + 1.0/3 * prevK;
            prevJ = 3 * prevK - 2 * prevD;
        }

        String signal;
        if (prevK > prevD && prevK < 30) signal = "低位金叉 - 买入信号";
        else if (prevK > prevD && prevK > 80) signal = "高位金叉 - 注意风险";
        else if (prevK < prevD && prevK > 80) signal = "高位死叉 - 卖出信号";
        else if (prevK < prevD && prevK < 20) signal = "低位死叉 - 可能超卖";
        else signal = "中性";

        result.put("strategy", "KDJ(9,3,3)");
        result.put("k", Math.round(prevK * 100) / 100.0);
        result.put("d", Math.round(prevD * 100) / 100.0);
        result.put("j", Math.round(prevJ * 100) / 100.0);
        result.put("signal", signal);
        result.put("signalType", prevK > prevD ? "bullish" : "bearish");
        return result;
    }

    private Map<String, Object> analyzeVolume(List<KLineService.KLineData> klines, List<Long> volumes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (volumes.size() < 20) { result.put("error", "Insufficient data for Volume"); return result; }

        long curVol = volumes.get(volumes.size() - 1);
        double avgVol = volumes.subList(volumes.size() - 21, volumes.size() - 1).stream()
                .mapToLong(Long::longValue).average().orElse(0);
        double ratio = avgVol > 0 ? curVol / avgVol : 0;

        double curPrice = klines.get(klines.size()-1).close();
        double prevPrice = klines.get(klines.size()-2).close();
        boolean priceUp = curPrice > prevPrice;

        String signal;
        if (ratio > 2.0 && priceUp) signal = "放量大涨 - 资金流入强烈";
        else if (ratio > 2.0 && !priceUp) signal = "放量大跌 - 资金流出强烈";
        else if (ratio > 1.5 && priceUp) signal = "温和放量上涨 - 多头偏强";
        else if (ratio > 1.5 && !priceUp) signal = "温和放量下跌 - 空头偏强";
        else if (ratio < 0.5) signal = "缩量 - 观望情绪浓";
        else signal = "量能正常";

        result.put("strategy", "量价分析");
        result.put("currentVolume", curVol);
        result.put("avgVolume20", (long) avgVol);
        result.put("volumeRatio", Math.round(ratio * 100) / 100.0);
        result.put("signal", signal);
        result.put("signalType", priceUp ? "bullish" : "bearish");
        return result;
    }

    // --- Helper calculations ---

    private double[] calcEMA(List<Double> values, int period) {
        double[] ema = new double[values.size()];
        double mult = 2.0 / (period + 1);
        ema[0] = values.get(0);
        for (int i = 1; i < values.size(); i++) ema[i] = values.get(i) * mult + ema[i-1] * (1 - mult);
        return ema;
    }

    private double[] calcEMAFromArray(double[] values, int period) {
        double[] ema = new double[values.length];
        double mult = 2.0 / (period + 1);
        ema[0] = values[0];
        for (int i = 1; i < values.length; i++) ema[i] = values[i] * mult + ema[i-1] * (1 - mult);
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
}
