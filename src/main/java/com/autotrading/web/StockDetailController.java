package com.autotrading.web;

import com.autotrading.market.KLineService;
import com.autotrading.market.MarketSessionService;
import com.autotrading.market.SnapshotPollingService;
import com.autotrading.market.StockAnalysisService;
import com.autotrading.market.TradingSignalService;
import com.autotrading.startup.QuoteProcessor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints for the stock detail page.
 */
@RestController
@RequestMapping("/api/stock")
public class StockDetailController {

    private final KLineService kLineService;
    private final StockAnalysisService analysisService;
    private final MarketSessionService sessionService;
    private final SnapshotPollingService snapshotService;
    private final QuoteProcessor quoteProcessor;
    private final TradingSignalService signalService;

    public StockDetailController(KLineService kLineService, StockAnalysisService analysisService,
                                  MarketSessionService sessionService, SnapshotPollingService snapshotService,
                                  QuoteProcessor quoteProcessor,
                                  TradingSignalService signalService) {
        this.kLineService = kLineService;
        this.analysisService = analysisService;
        this.sessionService = sessionService;
        this.snapshotService = snapshotService;
        this.quoteProcessor = quoteProcessor;
        this.signalService = signalService;
    }

    @GetMapping("/{market}/{code}/kline")
    public Map<String, Object> getKLine(@PathVariable int market, @PathVariable String code) {
        List<KLineService.KLineData> klines = kLineService.getKLines(market, code);

        List<Map<String, Object>> dataList = new ArrayList<>();
        for (KLineService.KLineData k : klines) {
            Map<String, Object> bar = new LinkedHashMap<>();
            bar.put("time", k.time());
            bar.put("open", k.open());
            bar.put("high", k.high());
            bar.put("low", k.low());
            bar.put("close", k.close());
            bar.put("volume", k.volume());
            bar.put("changeRate", k.changeRate());
            dataList.add(bar);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("market", market);
        result.put("count", dataList.size());

        // Calculate MA series for chart overlay
        List<Double> closes = klines.stream().map(KLineService.KLineData::close).toList();
        for (int period : List.of(5, 13, 30, 55)) {
            result.put("ma" + period, calculateMASeries(closes, period));
        }

        result.put("klines", dataList);
        result.put("session", sessionService.getSession(market).getLabel());

        // Latest price if available
        String stockKey = market + "." + code;
        QuoteProcessor.PriceSnapshot snap = quoteProcessor.getLatestPrices().get(stockKey);
        if (snap != null) {
            result.put("currentPrice", snap.curPrice());
        }

        return result;
    }

    @GetMapping("/{market}/{code}/analysis")
    public Map<String, Object> getAnalysis(
            @PathVariable int market, @PathVariable String code,
            @RequestParam(defaultValue = "ma_crossover") String strategy) {
        return analysisService.analyze(market, code, strategy);
    }

    @GetMapping("/{market}/{code}/signals")
    public Map<String, Object> getSignals(@PathVariable int market, @PathVariable String code) {
        List<TradingSignalService.Signal> signals = signalService.getSignals(market, code);

        List<Map<String, Object>> signalList = new ArrayList<>();
        for (TradingSignalService.Signal s : signals) {
            Map<String, Object> sig = new LinkedHashMap<>();
            sig.put("index", s.index());
            sig.put("date", s.date());
            sig.put("type", s.type().name());
            sig.put("price", s.price());
            sig.put("strategy", s.strategy());
            sig.put("reason", s.reason());
            signalList.add(sig);
        }

        long buyCount = signals.stream().filter(s -> s.type() == TradingSignalService.SignalType.BUY).count();
        long sellCount = signals.stream().filter(s -> s.type() == TradingSignalService.SignalType.SELL).count();

        TradingSignalService.Signal latest = signals.isEmpty() ? null : signals.get(signals.size() - 1);
        String latestAdvice = "暂无明确信号";
        if (latest != null) {
            latestAdvice = latest.type() == TradingSignalService.SignalType.BUY
                    ? "最近信号: 买入 (" + latest.reason() + ")"
                    : "最近信号: 卖出 (" + latest.reason() + ")";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signals", signalList);
        result.put("buyCount", buyCount);
        result.put("sellCount", sellCount);
        result.put("latestAdvice", latestAdvice);
        result.put("totalSignals", signals.size());
        return result;
    }

    @GetMapping("/strategies")
    public Map<String, Object> getStrategies() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> strategies = new LinkedHashMap<>();
        strategies.put("ma_crossover", "MA均线交叉");
        strategies.put("macd", "MACD");
        strategies.put("rsi", "RSI相对强弱");
        strategies.put("bollinger", "布林带");
        strategies.put("kdj", "KDJ随机指标");
        strategies.put("volume", "量价分析");
        result.put("strategies", strategies);
        return result;
    }

    private List<Map<String, Object>> calculateMASeries(List<Double> closes, int period) {
        List<Map<String, Object>> ma = new ArrayList<>();
        for (int i = period - 1; i < closes.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += closes.get(j);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("index", i);
            point.put("value", Math.round(sum / period * 10000) / 10000.0);
            ma.add(point);
        }
        return ma;
    }
}
