package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.config.FutuProperties.FluctuationRule;
import com.autotrading.entity.FluctuationConfigEntity;
import com.autotrading.repository.FluctuationConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Time-windowed fluctuation monitor.
 * <p>
 * Maintains a rolling price history per stock and evaluates multiple time-window
 * rules. Rules are combined with AND or OR logic:
 * <ul>
 *   <li>OR: stock qualifies if ANY rule matches</li>
 *   <li>AND: stock qualifies only if ALL rules match</li>
 * </ul>
 *
 * Each rule defines a time window (minutes) and a threshold percent.
 * The monitor checks whether the price moved by >= threshold within that window.
 */
@Component
public class TimeWindowFluctuationMonitor {

    private static final Logger log = LoggerFactory.getLogger(TimeWindowFluctuationMonitor.class);
    private static final int MAX_HISTORY_PER_STOCK = 600;
    private static final Long CONFIG_ID = 1L;

    private final FutuProperties properties;
    private final FluctuationConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    private volatile FutuProperties.Fluctuation config;

    /** stockKey -> deque of recent price ticks (oldest first). */
    private final Map<String, ConcurrentLinkedDeque<PriceTick>> priceHistory = new ConcurrentHashMap<>();

    public TimeWindowFluctuationMonitor(FutuProperties properties,
                                         FluctuationConfigRepository configRepository,
                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.config = properties.getFluctuation();
    }

    /**
     * Loads persisted rules from the database on startup. When the database has
     * no saved config yet (first run), the seed default from configuration is
     * persisted so subsequent reads always come from the database.
     */
    @PostConstruct
    public void loadFromDatabase() {
        try {
            FluctuationConfigEntity entity = configRepository.findById(CONFIG_ID).orElse(null);
            if (entity != null) {
                this.config = toConfig(entity);
                log.info("Loaded fluctuation config from database: logic={}, rules={}",
                        config.getLogic(), config.getRules().size());
            } else {
                persistConfig(this.config);
                log.info("No persisted fluctuation config; seeded default: logic={}, rules={}",
                        config.getLogic(), config.getRules().size());
            }
        } catch (Exception e) {
            log.warn("Failed to load fluctuation config from database, using seed default: {}", e.getMessage());
        }
    }

    private FutuProperties.Fluctuation toConfig(FluctuationConfigEntity entity) {
        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic(entity.getLogic());
        cfg.setEvalIntervalMs(this.config.getEvalIntervalMs());
        try {
            List<FluctuationRule> rules = objectMapper.readValue(
                    entity.getRulesJson(), new TypeReference<List<FluctuationRule>>() {});
            cfg.setRules(rules);
        } catch (Exception e) {
            log.warn("Failed to parse persisted rules JSON, falling back to seed default: {}", e.getMessage());
            cfg.setRules(properties.getFluctuation().getRules());
        }
        return cfg;
    }

    private void persistConfig(FutuProperties.Fluctuation cfg) {
        FluctuationConfigEntity entity = new FluctuationConfigEntity();
        entity.setId(CONFIG_ID);
        entity.setLogic(cfg.getLogic());
        try {
            entity.setRulesJson(objectMapper.writeValueAsString(cfg.getRules()));
        } catch (Exception e) {
            log.warn("Failed to serialize fluctuation rules to JSON: {}", e.getMessage());
            entity.setRulesJson("[]");
        }
        configRepository.save(entity);
    }

    /**
     * Updates the fluctuation rules at runtime (called from config API).
     */
    public synchronized void updateConfig(FutuProperties.Fluctuation newConfig) {
        this.config = newConfig;
        try {
            persistConfig(newConfig);
        } catch (Exception e) {
            log.warn("Failed to persist fluctuation config to database: {}", e.getMessage());
        }
        log.info("Fluctuation config updated and persisted: logic={}, rules={}",
                newConfig.getLogic(), newConfig.getRules().size());
    }

    public FutuProperties.Fluctuation getConfig() {
        return config;
    }

    /**
     * Records a price tick for a stock.
     */
    public void recordPrice(String stockKey, double price, long timestamp) {
        ConcurrentLinkedDeque<PriceTick> deque = priceHistory.computeIfAbsent(stockKey,
                k -> new ConcurrentLinkedDeque<>());
        deque.addLast(new PriceTick(price, timestamp));

        // Trim to max size and prune old data beyond the largest window
        int maxWindowMin = config.getRules().stream()
                .mapToInt(FluctuationRule::getWindowMinutes)
                .max().orElse(10);
        long cutoff = timestamp - (maxWindowMin + 5) * 60_000L;
        while (!deque.isEmpty() && deque.peekFirst().timestamp < cutoff) {
            deque.pollFirst();
        }
        // Hard cap
        while (deque.size() > MAX_HISTORY_PER_STOCK) {
            deque.pollFirst();
        }
    }

    /**
     * Evaluates all rules for a single stock.
     *
     * @return matching rule details if the stock qualifies, null otherwise
     */
    public StockFluctuationResult evaluate(String stockKey, String stockName) {
        ConcurrentLinkedDeque<PriceTick> deque = priceHistory.get(stockKey);
        if (deque == null || deque.size() < 2) {
            return null;
        }

        List<PriceTick> ticks = new ArrayList<>(deque);
        PriceTick latest = ticks.get(ticks.size() - 1);
        if (latest.price <= 0) return null;

        List<FluctuationRule> rules = config.getRules();
        if (rules.isEmpty()) return null;

        String logic = config.getLogic();
        List<RuleMatch> matched = new ArrayList<>();
        List<RuleMatch> all = new ArrayList<>();

        for (FluctuationRule rule : rules) {
            long windowMs = rule.getWindowMinutes() * 60_000L;
            long windowStart = latest.timestamp - windowMs;

            // Find the oldest tick within the window
            PriceTick reference = null;
            for (PriceTick t : ticks) {
                if (t.timestamp >= windowStart) {
                    reference = t;
                    break;
                }
            }
            if (reference == null || reference.price <= 0) {
                all.add(new RuleMatch(rule, 0, false));
                continue;
            }

            double changePct = (latest.price - reference.price) / reference.price * 100.0;
            boolean match = Math.abs(changePct) >= rule.getThresholdPercent();
            RuleMatch rm = new RuleMatch(rule, changePct, match);
            all.add(rm);
            if (match) matched.add(rm);
        }

        boolean qualifies;
        if ("AND".equalsIgnoreCase(logic)) {
            qualifies = matched.size() == rules.size();
        } else {
            // OR (default)
            qualifies = !matched.isEmpty();
        }

        if (!qualifies) return null;

        String direction = matched.stream()
                .mapToDouble(RuleMatch::changePct)
                .average().orElse(0) >= 0 ? "涨" : "跌";
        double maxChange = all.stream()
                .mapToDouble(m -> Math.abs(m.changePct))
                .max().orElse(0);

        return new StockFluctuationResult(stockKey, stockName, latest.price, direction,
                matched, all, System.currentTimeMillis());
    }

    /**
     * Clears history for a stock.
     */
    public void resetStock(String stockKey) {
        priceHistory.remove(stockKey);
    }

    public void resetAll() {
        priceHistory.clear();
    }

    /** A single price observation. */
    public record PriceTick(double price, long timestamp) {}

    /** Whether a specific rule matched for a stock. */
    public record RuleMatch(FluctuationRule rule, double changePct, boolean matched) {}

    /** Evaluation result for a qualifying stock. */
    public record StockFluctuationResult(
            String stockKey, String stockName, double currentPrice,
            String direction, List<RuleMatch> matchedRules, List<RuleMatch> allRules,
            long timestamp) {}
}
