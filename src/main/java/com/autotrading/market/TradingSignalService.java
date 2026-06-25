package com.autotrading.market;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Detects buy/sell trading signals from historical K-line data.
 * Scans for crossovers and indicator transitions across the full K-line series.
 *
 * Signal types:
 *  - BUY:  MA golden cross, MACD golden cross, RSI exits oversold, KDJ low golden cross, Bollinger lower bounce
 *  - SELL: MA death cross, MACD death cross, RSI exits overbought, KDJ high death cross, Bollinger upper touch
 *
 * Each signal includes a reason and the index in the K-line array.
 */
@Service
public class TradingSignalService {

    public enum SignalType { BUY, SELL }

    public record Signal(
            int index,
            String date,
            SignalType type,
            double price,
            String strategy,
            String reason
    ) {}

    private final KLineService kLineService;

    public TradingSignalService(KLineService kLineService) {
        this.kLineService = kLineService;
    }

    /**
     * Computes all buy/sell signals for a stock, sorted by date.
     * Returns at most the 20 most recent signals.
     */
    public List<Signal> getSignals(int market, String code) {
        List<KLineService.KLineData> klines = kLineService.getKLines(market, code);
        if (klines.isEmpty() || klines.size() < 30) {
            return List.of();
        }

        List<Double> closes = klines.stream().map(KLineService.KLineData::close).toList();
        List<Signal> signals = new ArrayList<>();

        signals.addAll(detectMASignals(klines, closes));
        signals.addAll(detectMACDSignals(klines, closes));
        signals.addAll(detectRSISignals(klines, closes));
        signals.addAll(detectKDJSignals(klines));
        signals.addAll(detectBollingerSignals(klines, closes));

        // Sort by index (date), then take most recent 20
        signals.sort(Comparator.comparingInt(Signal::index));
        if (signals.size() > 20) {
            signals = new ArrayList<>(signals.subList(signals.size() - 20, signals.size()));
        }

        // Deduplicate: remove multiple signals on the same day with the same type
        List<Signal> deduped = new ArrayList<>();
        Signal lastSig = null;
        for (Signal s : signals) {
            if (lastSig != null && s.index() == lastSig.index() && s.type() == lastSig.type()) {
                continue;
            }
            deduped.add(s);
            lastSig = s;
        }
        return deduped;
    }

    // --- MA Golden/Death Cross (MA5 vs MA13) ---

    private List<Signal> detectMASignals(List<KLineService.KLineData> klines, List<Double> closes) {
        List<Signal> signals = new ArrayList<>();
        double[] ma5 = calcSMAArray(closes, 5);
        double[] ma13 = calcSMAArray(closes, 13);

        for (int i = 13; i < closes.size(); i++) {
            if (Double.isNaN(ma5[i]) || Double.isNaN(ma13[i]) ||
                Double.isNaN(ma5[i-1]) || Double.isNaN(ma13[i-1])) continue;

            // Golden cross: MA5 crosses above MA13
            if (ma5[i - 1] <= ma13[i - 1] && ma5[i] > ma13[i]) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.BUY,
                        closes.get(i), "MA交叉", "MA5金叉MA13 短期趋势转多"));
            }
            // Death cross: MA5 crosses below MA13
            if (ma5[i - 1] >= ma13[i - 1] && ma5[i] < ma13[i]) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.SELL,
                        closes.get(i), "MA交叉", "MA5死叉MA13 短期趋势转空"));
            }
        }
        return signals;
    }

    // --- MACD Golden/Death Cross ---

    private List<Signal> detectMACDSignals(List<KLineService.KLineData> klines, List<Double> closes) {
        List<Signal> signals = new ArrayList<>();
        if (closes.size() < 35) return signals;

        double[] ema12 = calcEMA(closes, 12);
        double[] ema26 = calcEMA(closes, 26);
        double[] dif = new double[closes.size()];
        for (int i = 0; i < closes.size(); i++) dif[i] = ema12[i] - ema26[i];
        double[] dea = calcEMAFromArray(dif, 9);

        for (int i = 1; i < closes.size(); i++) {
            // Golden cross: DIF crosses above DEA
            if (dif[i - 1] <= dea[i - 1] && dif[i] > dea[i]) {
                String context = dif[i] > 0 ? "零轴上方" : "零轴下方";
                signals.add(new Signal(i, klines.get(i).time(), SignalType.BUY,
                        closes.get(i), "MACD", "MACD金叉(" + context + ") 多头信号"));
            }
            // Death cross: DIF crosses below DEA
            if (dif[i - 1] >= dea[i - 1] && dif[i] < dea[i]) {
                String context = dif[i] < 0 ? "零轴下方" : "零轴上方";
                signals.add(new Signal(i, klines.get(i).time(), SignalType.SELL,
                        closes.get(i), "MACD", "MACD死叉(" + context + ") 空头信号"));
            }
        }
        return signals;
    }

    // --- RSI Overbought/Oversold exit ---

    private List<Signal> detectRSISignals(List<KLineService.KLineData> klines, List<Double> closes) {
        List<Signal> signals = new ArrayList<>();
        int period = 14;
        if (closes.size() < period + 1) return signals;

        double[] rsi = new double[closes.size()];
        for (int i = period; i < closes.size(); i++) {
            rsi[i] = calcRSIAt(closes, i, period);
        }

        for (int i = period + 1; i < closes.size(); i++) {
            // Exit oversold: RSI crosses back above 30
            if (rsi[i - 1] < 30 && rsi[i] >= 30) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.BUY,
                        closes.get(i), "RSI", "RSI脱离超卖区(" + round(rsi[i]) + ") 可能反弹"));
            }
            // Exit overbought: RSI crosses back below 70
            if (rsi[i - 1] > 70 && rsi[i] <= 70) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.SELL,
                        closes.get(i), "RSI", "RSI脱离超买区(" + round(rsi[i]) + ") 注意回调"));
            }
        }
        return signals;
    }

    // --- KDJ Golden/Death Cross ---

    private List<Signal> detectKDJSignals(List<KLineService.KLineData> klines) {
        List<Signal> signals = new ArrayList<>();
        int n = 9;
        if (klines.size() < n + 1) return signals;

        double[] k = new double[klines.size()];
        double[] d = new double[klines.size()];
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
            k[i] = prevK;
            d[i] = prevD;
        }

        for (int i = n; i < klines.size(); i++) {
            // Low golden cross: K crosses above D in oversold zone
            if (k[i - 1] <= d[i - 1] && k[i] > d[i] && k[i] < 35) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.BUY,
                        klines.get(i).close(), "KDJ", "KDJ低位金叉(K=" + round(k[i]) + ") 买入信号"));
            }
            // High death cross: K crosses below D in overbought zone
            if (k[i - 1] >= d[i - 1] && k[i] < d[i] && k[i] > 65) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.SELL,
                        klines.get(i).close(), "KDJ", "KDJ高位死叉(K=" + round(k[i]) + ") 卖出信号"));
            }
        }
        return signals;
    }

    // --- Bollinger Band touches ---

    private List<Signal> detectBollingerSignals(List<KLineService.KLineData> klines, List<Double> closes) {
        List<Signal> signals = new ArrayList<>();
        int period = 20;
        if (closes.size() < period + 1) return signals;

        for (int i = period; i < closes.size(); i++) {
            double[] boll = calcBollingerAt(closes, i, period);
            double upper = boll[0], lower = boll[2];

            // Price touches lower band then bounces
            if (closes.get(i - 1) <= lower && closes.get(i) > lower) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.BUY,
                        closes.get(i), "布林带", "触及布林带下轨反弹 可能见底"));
            }
            // Price touches upper band then retreats
            if (closes.get(i - 1) >= upper && closes.get(i) < upper) {
                signals.add(new Signal(i, klines.get(i).time(), SignalType.SELL,
                        closes.get(i), "布林带", "触及布林带上轨回落 注意风险"));
            }
        }
        return signals;
    }

    // --- Calculation helpers ---

    private double[] calcSMAArray(List<Double> closes, int period) {
        double[] sma = new double[closes.size()];
        for (int i = 0; i < closes.size(); i++) {
            if (i < period - 1) { sma[i] = Double.NaN; continue; }
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += closes.get(j);
            sma[i] = sum / period;
        }
        return sma;
    }

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

    private double calcRSIAt(List<Double> closes, int endIdx, int period) {
        double gain = 0, loss = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) gain += change; else loss -= change;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        if (avgLoss == 0) return 100;
        return 100 - 100 / (1 + avgGain / avgLoss);
    }

    private double[] calcBollingerAt(List<Double> closes, int endIdx, int period) {
        double sum = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) sum += closes.get(i);
        double sma = sum / period;
        double sqSum = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) sqSum += Math.pow(closes.get(i) - sma, 2);
        double sd = Math.sqrt(sqSum / period);
        return new double[]{ sma + 2 * sd, sma, sma - 2 * sd };
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
