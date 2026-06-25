package com.autotrading.market;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Detects buy/sell trading signals based on volume-price relationship analysis.
 *
 * Core principle: Volume leads price. We detect divergence between volume
 * and price action to identify distribution (sell) and accumulation (buy).
 *
 * SELL patterns (量价不对称 / 高位滞涨):
 *  - 高位放量滞涨: large volume, small body, price at recent high → distribution
 *  - 天量天价:   volume 3x+ average at price peak → climax top
 *  - 放量大跌:   volume 2x+ average with big drop → panic selling
 *  - 量价背离:   price makes higher highs, volume makes lower highs → uptrend exhausting
 *  - 缩量滞涨:   price stalls, volume keeps shrinking after a rally → momentum dying
 *
 * BUY patterns (缩量下跌 / 下跌动能不足):
 *  - 缩量下跌:   price drops but volume keeps shrinking for 3+ days → selling pressure exhausted
 *  - 地量地价:   volume hits multi-week low near price low → potential bottom
 *  - 底部放量:   volume spikes near price low but price holds/bounces → accumulation
 *  - 缩量回调:   price dips on low volume during an uptrend → healthy pullback, buy the dip
 *  - 缩量止跌:   price stops falling, volume dries up → capitulation
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

    private static final int LOOKBACK = 20;     // bars for average volume / price range
    private static final int MIN_BARS = LOOKBACK + 5;

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
        if (klines.isEmpty() || klines.size() < MIN_BARS) {
            return List.of();
        }

        List<Signal> signals = new ArrayList<>();

        for (int i = LOOKBACK; i < klines.size(); i++) {
            BarCtx c = buildContext(klines, i);

            // --- SELL signals ---
            checkHighVolumeStagnation(c, signals);   // 高位放量滞涨
            checkClimaxVolume(c, signals);           // 天量天价
            checkHeavyVolumeDrop(c, signals);        // 放量大跌
            checkVolumePriceDivergence(c, signals);  // 量价背离
            checkShrinkStall(c, signals);            // 缩量滞涨

            // --- BUY signals ---
            checkShrinkDecline(c, signals);          // 缩量下跌
            checkUltraLowVolume(c, signals);         // 地量地价
            checkBottomVolume(c, signals);           // 底部放量
            checkPullbackLowVolume(c, signals);      // 缩量回调
            checkVolumeDryUp(c, signals);            // 缩量止跌
        }

        // Sort by index (date), then take most recent 20
        signals.sort(Comparator.comparingInt(Signal::index));
        if (signals.size() > 20) {
            signals = new ArrayList<>(signals.subList(signals.size() - 20, signals.size()));
        }

        // Deduplicate: same day + same type → keep only the first
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

    // ========================================================================
    //  SELL signals
    // ========================================================================

    /**
     * 高位放量滞涨: price is near recent high, volume above average, but candle body
     * is small → big players distributing, not accumulating.
     */
    private void checkHighVolumeStagnation(BarCtx c, List<Signal> signals) {
        if (c.pricePosition < 0.75) return;
        if (c.volRatio < 1.4) return;
        // Small body relative to candle range → stagnation
        if (c.bodyToRange < 0.4) return;
        // Price barely moved (change within ±1%)
        if (Math.abs(c.changePct) > 1.5) return;

        signals.add(c.sell("放量滞涨",
                String.format("高位放量滞涨 价格接近近期高点(%d%%), 量比%.1f倍, 但实体仅占振幅%.0f%%, 大资金可能出货",
                        (int) (c.pricePosition * 100), c.volRatio, c.bodyToRange * 100)));
    }

    /**
     * 天量天价: volume is 3x+ the 20-day average AND price is at the top of the range.
     * This is a classic blow-off top / climax signal.
     */
    private void checkClimaxVolume(BarCtx c, List<Signal> signals) {
        if (c.volRatio < 3.0) return;
        if (c.pricePosition < 0.80) return;

        signals.add(c.sell("天量天价",
                String.format("天量天价 量比%.1f倍(20日均量), 价格在近期高点, 换手极度活跃, 警惕顶部",
                        c.volRatio)));
    }

    /**
     * 放量大跌: volume 2x+ average, price drops more than 2% → panic / heavy selling.
     */
    private void checkHeavyVolumeDrop(BarCtx c, List<Signal> signals) {
        if (c.volRatio < 2.0) return;
        if (c.changePct > -2.0) return;
        if (c.isUp) return;

        signals.add(c.sell("放量大跌",
                String.format("放量大跌 跌幅%.1f%%, 量比%.1f倍, 资金大幅流出",
                        c.changePct, c.volRatio)));
    }

    /**
     * 量价背离: over the last 5 bars, price made higher highs but volume made lower highs.
     * Classic divergence → uptrend running out of fuel.
     */
    private void checkVolumePriceDivergence(BarCtx c, List<Signal> signals) {
        if (c.n < 5) return;
        // Check last 5 bars: are closes trending up?
        boolean priceUp = c.closes[0] < c.closes[4];
        if (!priceUp) return;
        // Are volumes trending down?
        boolean volDown = c.volumes[0] > c.volumes[4];
        if (!volDown) return;
        // Current bar should be near the top
        if (c.pricePosition < 0.6) return;

        signals.add(c.sell("量价背离",
                "量价背离 近5日价格上涨但成交量逐日萎缩, 上涨缺乏量能支撑, 注意见顶风险"));
    }

    /**
     * 缩量滞涨: after a rally, volume shrinks for 3+ days while price stalls (small bodies).
     * Momentum is dying.
     */
    private void checkShrinkStall(BarCtx c, List<Signal> signals) {
        if (c.pricePosition < 0.6) return;
        if (c.n < 3) return;
        // Volume shrinking over last 3 bars
        if (!(c.volRatios[2] < 0.8 && c.volRatios[1] < c.volRatios[2])) return;
        // Price body small (stagnation)
        if (c.bodyToRange > 0.5) return;
        // Not a big drop (otherwise it's a different pattern)
        if (c.changePct < -2.0) return;

        signals.add(c.sell("缩量滞涨",
                String.format("缩量滞涨 连续缩量(量比%.1f→%.1f)且股价高位横盘, 上涨动能衰竭",
                        c.volRatios[1], c.volRatios[2])));
    }

    // ========================================================================
    //  BUY signals
    // ========================================================================

    /**
     * 缩量下跌: price falls but volume keeps shrinking for 3+ consecutive days.
     * Selling pressure is exhausting → potential reversal.
     */
    private void checkShrinkDecline(BarCtx c, List<Signal> signals) {
        if (c.n < 3) return;
        // Last 3 bars all red (down)
        boolean allDown = true;
        for (int i = 2; i <= 4; i++) {
            if (c.closes[i] >= c.closes[i - 1]) { allDown = false; break; }
        }
        if (!allDown) return;
        // Volume shrinking each day
        if (!(c.volumes[2] > c.volumes[3] && c.volumes[3] > c.volumes[4])) return;
        // Current bar volume below average
        if (c.volRatio > 0.7) return;

        signals.add(c.buy("缩量下跌",
                String.format("缩量下跌 连续3日下跌但量能递减(量比%.1f), 下跌动能不足, 可能见底",
                        c.volRatio)));
    }

    /**
     * 地量地价: volume hits an ultra-low level (below 0.4x average) near a price low.
     * Classic "volume dries up at the bottom" pattern.
     */
    private void checkUltraLowVolume(BarCtx c, List<Signal> signals) {
        if (c.volRatio > 0.4) return;
        if (c.pricePosition > 0.35) return;

        signals.add(c.buy("地量地价",
                String.format("地量地价 量比仅%.2f倍(极度萎缩), 价格在近期低位(%d%%), 抛压枯竭",
                        c.volRatio, (int) (c.pricePosition * 100))));
    }

    /**
     * 底部放量: volume spikes (2x+) near a price low, but price does NOT drop further
     * (or even bounces) → smart money accumulating at the bottom.
     */
    private void checkBottomVolume(BarCtx c, List<Signal> signals) {
        if (c.volRatio < 1.8) return;
        if (c.pricePosition > 0.4) return;
        // Price must not be crashing (change within -2% to positive)
        if (c.changePct < -2.0) return;

        signals.add(c.buy("底部放量",
                String.format("底部放量 低位放量(量比%.1f倍)但股价未继续大跌(%.1f%%), 资金进场吸筹",
                        c.volRatio, c.changePct)));
    }

    /**
     * 缩量回调: price is in an uptrend (above 60th percentile of range), today's price
     * dips slightly but on low volume (<0.7x average) → healthy pullback, buy opportunity.
     */
    private void checkPullbackLowVolume(BarCtx c, List<Signal> signals) {
        if (c.pricePosition < 0.55) return;
        if (c.isUp) return;             // must be a down day
        if (c.changePct > -0.5) return; // meaningful dip
        if (c.changePct < -3.0) return; // not a crash
        if (c.volRatio > 0.7) return;   // low volume

        signals.add(c.buy("缩量回调",
                String.format("缩量回调 上升趋势中回调(跌%.1f%%)但缩量(量比%.1f), 回调充分后可能继续上涨",
                        c.changePct, c.volRatio)));
    }

    /**
     * 缩量止跌: price stops falling (small body or doji) on very low volume near the
     * bottom → selling has dried up, reversal imminent.
     */
    private void checkVolumeDryUp(BarCtx c, List<Signal> signals) {
        if (c.volRatio > 0.5) return;
        if (c.pricePosition > 0.4) return;
        // Very small body → price stopped moving
        if (c.bodyToRange > 0.45) return;
        if (Math.abs(c.changePct) > 1.0) return;

        signals.add(c.buy("缩量止跌",
                String.format("缩量止跌 低位缩量(量比%.1f)且股价几乎不动, 卖盘枯竭可能见底",
                        c.volRatio)));
    }

    // ========================================================================
    //  Context builder
    // ========================================================================

    /**
     * Pre-computes all metrics needed for signal detection on bar [i].
     */
    private record BarCtx(
            int index,
            KLineService.KLineData bar,
            // Volume
            double volRatio,        // current volume / 20-day average
            // Price
            double changePct,       // daily change %
            boolean isUp,           // close >= open
            double bodyToRange,     // body size / candle range (0-1)
            double pricePosition,   // position within recent 20-bar range (0-1)
            // Recent 5-bar arrays (index 0 = oldest, 4 = current)
            double[] closes,
            long[] volumes,
            double[] volRatios,
            int n                   // number of recent bars available
    ) {
        Signal buy(String strategy, String reason) {
            return new Signal(index, bar.time(), SignalType.BUY, bar.close(), strategy, reason);
        }
        Signal sell(String strategy, String reason) {
            return new Signal(index, bar.time(), SignalType.SELL, bar.close(), strategy, reason);
        }
    }

    private BarCtx buildContext(List<KLineService.KLineData> klines, int i) {
        KLineService.KLineData bar = klines.get(i);

        // 20-day average volume
        long volSum = 0;
        for (int j = i - LOOKBACK; j < i; j++) volSum += klines.get(j).volume();
        double avgVol = (double) volSum / LOOKBACK;
        double volRatio = avgVol > 0 ? bar.volume() / avgVol : 0;

        // Daily change %
        double prevClose = klines.get(i - 1).close();
        double changePct = (bar.close() - prevClose) / prevClose * 100;
        boolean isUp = bar.close() >= bar.open();

        // Body-to-range ratio
        double body = Math.abs(bar.close() - bar.open());
        double range = bar.high() - bar.low();
        double bodyToRange = range > 0 ? body / range : 1.0;

        // Price position within 20-bar range
        double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
        for (int j = i - LOOKBACK; j <= i; j++) {
            minPrice = Math.min(minPrice, klines.get(j).low());
            maxPrice = Math.max(maxPrice, klines.get(j).high());
        }
        double priceSpan = maxPrice - minPrice;
        double pricePosition = priceSpan > 0 ? (bar.close() - minPrice) / priceSpan : 0.5;

        // Recent 5-bar arrays (index 0 = oldest = i-4, index 4 = current = i)
        int n = Math.min(5, i + 1);
        double[] closes = new double[n];
        long[] volumes = new long[n];
        double[] volRatios = new double[n];
        for (int k = 0; k < n; k++) {
            int idx = i - n + 1 + k;
            closes[k] = klines.get(idx).close();
            volumes[k] = klines.get(idx).volume();
            // Vol ratio for this bar
            long vs = 0;
            int volStart = Math.max(0, idx - LOOKBACK);
            int count = 0;
            for (int j = volStart; j < idx; j++) { vs += klines.get(j).volume(); count++; }
            double aVol = count > 0 ? (double) vs / count : 0;
            volRatios[k] = aVol > 0 ? klines.get(idx).volume() / aVol : 0;
        }

        return new BarCtx(i, bar, volRatio, changePct, isUp, bodyToRange,
                pricePosition, closes, volumes, volRatios, n);
    }
}
