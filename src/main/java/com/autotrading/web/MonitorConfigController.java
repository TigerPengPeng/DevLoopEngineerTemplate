package com.autotrading.web;

import com.autotrading.config.FutuProperties;
import com.autotrading.config.FutuProperties.FluctuationRule;
import com.autotrading.monitor.MABreakdownScanner;
import com.autotrading.monitor.TimeWindowFluctuationMonitor;
import com.autotrading.notification.NotificationTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API endpoints for runtime monitoring configuration and manual triggers.
 */
@RestController
public class MonitorConfigController {

    private final FutuProperties properties;
    private final TimeWindowFluctuationMonitor fluctuationMonitor;
    private final MABreakdownScanner maBreakdownScanner;

    public MonitorConfigController(FutuProperties properties,
                                    TimeWindowFluctuationMonitor fluctuationMonitor,
                                    MABreakdownScanner maBreakdownScanner) {
        this.properties = properties;
        this.fluctuationMonitor = fluctuationMonitor;
        this.maBreakdownScanner = maBreakdownScanner;
    }

    // ---- Fluctuation Rules ----

    @GetMapping("/api/fluctuation-config")
    public Map<String, Object> getFluctuationConfig() {
        FutuProperties.Fluctuation cfg = fluctuationMonitor.getConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logic", cfg.getLogic());
        List<Map<String, Object>> rules = new ArrayList<>();
        for (FluctuationRule r : cfg.getRules()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("windowMinutes", r.getWindowMinutes());
            rule.put("thresholdPercent", r.getThresholdPercent());
            rules.add(rule);
        }
        result.put("rules", rules);
        result.put("evalIntervalMs", cfg.getEvalIntervalMs());
        return result;
    }

    @PostMapping("/api/fluctuation-config")
    public Map<String, Object> updateFluctuationConfig(@RequestBody Map<String, Object> body) {
        String logic = (String) body.getOrDefault("logic", "OR");
        List<FluctuationRule> rules = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleList = (List<Map<String, Object>>) body.get("rules");
        if (ruleList != null) {
            for (Map<String, Object> r : ruleList) {
                int window = ((Number) r.get("windowMinutes")).intValue();
                double threshold = ((Number) r.get("thresholdPercent")).doubleValue();
                rules.add(new FluctuationRule(window, threshold));
            }
        }

        FutuProperties.Fluctuation newCfg = new FutuProperties.Fluctuation();
        newCfg.setLogic(logic);
        newCfg.setRules(rules);
        newCfg.setEvalIntervalMs(fluctuationMonitor.getConfig().getEvalIntervalMs());

        fluctuationMonitor.updateConfig(newCfg);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("logic", logic);
        result.put("rules", rules.size());
        return result;
    }

    // ---- MA Breakdown Scan ----

    @PostMapping("/api/ma-scan")
    public Map<String, Object> triggerMAScan() {
        List<NotificationTemplate.MABreakdownItem> items = maBreakdownScanner.runScan("手动触发");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("brokenStocks", items.size());
        result.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return result;
    }
}
