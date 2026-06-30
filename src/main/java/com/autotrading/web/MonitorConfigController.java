package com.autotrading.web;

import com.autotrading.config.FutuProperties;
import com.autotrading.config.FutuProperties.FluctuationRule;
import com.autotrading.monitor.MABreakdownScanner;
import com.autotrading.monitor.MARuleEngine;
import com.autotrading.monitor.TimeWindowFluctuationMonitor;
import com.autotrading.model.Direction;
import com.autotrading.notification.EmailNotificationService;
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
    private final MARuleEngine maRuleEngine;
    private final EmailNotificationService emailNotificationService;

    public MonitorConfigController(FutuProperties properties,
                                    TimeWindowFluctuationMonitor fluctuationMonitor,
                                    MABreakdownScanner maBreakdownScanner,
                                    MARuleEngine maRuleEngine,
                                    EmailNotificationService emailNotificationService) {
        this.properties = properties;
        this.fluctuationMonitor = fluctuationMonitor;
        this.maBreakdownScanner = maBreakdownScanner;
        this.maRuleEngine = maRuleEngine;
        this.emailNotificationService = emailNotificationService;
    }

    // ---- Email Toggle ----

    @GetMapping("/api/email-toggle")
    public Map<String, Object> getEmailToggle() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", emailNotificationService.isEmailEnabled());
        result.put("configured", emailNotificationService.isConfigured());
        return result;
    }

    @PostMapping("/api/email-toggle")
    public Map<String, Object> toggleEmail(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        emailNotificationService.setEmailEnabled(enabled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("enabled", emailNotificationService.isEmailEnabled());
        return result;
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

    // ---- MA Alert Rules ----

    @GetMapping("/api/ma-alert-config")
    public Map<String, Object> getMAAlertConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logic", maRuleEngine.getTopLogic());
        List<Map<String, Object>> rules = new ArrayList<>();
        for (MARuleEngine.Rule r : maRuleEngine.getRules()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("name", r.getName());
            rule.put("logic", r.getLogic());
            List<Map<String, Object>> conds = new ArrayList<>();
            for (MARuleEngine.Condition c : r.getConditions()) {
                Map<String, Object> cond = new LinkedHashMap<>();
                cond.put("frequency", c.getFrequency());
                cond.put("maPeriod", c.getMaPeriod());
                cond.put("direction", c.getDirection().name());
                conds.add(cond);
            }
            rule.put("conditions", conds);
            rules.add(rule);
        }
        result.put("rules", rules);
        return result;
    }

    @PostMapping("/api/ma-alert-config")
    public Map<String, Object> updateMAAlertConfig(@RequestBody Map<String, Object> body) {
        String logic = (String) body.getOrDefault("logic", "OR");
        List<MARuleEngine.Rule> newRules = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ruleList = (List<Map<String, Object>>) body.get("rules");
        if (ruleList != null) {
            for (Map<String, Object> r : ruleList) {
                String name = (String) r.getOrDefault("name", "");
                String rLogic = (String) r.getOrDefault("logic", "OR");
                List<MARuleEngine.Condition> conds = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> condList = (List<Map<String, Object>>) r.get("conditions");
                if (condList != null) {
                    for (Map<String, Object> c : condList) {
                        String freq = (String) c.getOrDefault("frequency", "day");
                        int period = ((Number) c.get("maPeriod")).intValue();
                        Direction dir = Direction.valueOf((String) c.get("direction"));
                        conds.add(new MARuleEngine.Condition(freq, period, dir));
                    }
                }
                newRules.add(new MARuleEngine.Rule(name, rLogic, conds));
            }
        }

        maRuleEngine.updateConfig(logic, newRules);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("logic", maRuleEngine.getTopLogic());
        result.put("rules", maRuleEngine.getRules().size());
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
