package com.autotrading.monitor;

import com.autotrading.entity.MAAlertConfigEntity;
import com.autotrading.indicator.CrossoverDetector;
import com.autotrading.indicator.MACalculator;
import com.autotrading.market.KLineService;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import com.autotrading.repository.MAAlertConfigRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configurable MA alert rule engine — replaces the hardcoded MA5/13/30/55
 * crossover alerts.
 * <p>
 * Each <em>condition</em> is "price crosses above/below MA(N) at a given
 * frequency (day or week)". Each <em>rule</em> combines one or more conditions
 * with AND or OR logic. Rules combine at the top level with OR (any rule
 * matching fires an alert).
 * <p>
 * <b>AND semantics (event + state confirmation):</b> a condition produces a
 * crossover <em>event</em> (the transition tick) and also holds a current
 * <em>state</em> (price is on the configured side). For OR rules, any
 * condition's crossover event fires the alert. For AND rules, one condition's
 * crossover event fires <em>only if</em> all other conditions are currently in
 * their matching state. This lets a user express "daily MA13 break-up AND price
 * above weekly MA30" without requiring two crossovers on the same tick.
 * <p>
 * Config is persisted as a singleton (id=1) in {@link MAAlertConfigEntity},
 * mirroring the fluctuation-config pattern.
 */
@Component
public class MARuleEngine {

    private static final Logger log = LoggerFactory.getLogger(MARuleEngine.class);
    private static final Long CONFIG_ID = 1L;
    private static final String FREQ_DAY = "day";
    private static final String FREQ_WEEK = "week";

    private final KLineService kLineService;
    private final CrossoverDetector crossoverDetector;
    private final MAAlertConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    private volatile String topLogic = "OR";
    private volatile List<Rule> rules = List.of();

    public MARuleEngine(KLineService kLineService,
                        CrossoverDetector crossoverDetector,
                        MAAlertConfigRepository configRepository,
                        ObjectMapper objectMapper) {
        this.kLineService = kLineService;
        this.crossoverDetector = crossoverDetector;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadFromDatabase() {
        try {
            MAAlertConfigEntity entity = configRepository.findById(CONFIG_ID).orElse(null);
            if (entity != null) {
                this.topLogic = entity.getLogic();
                this.rules = parseRules(entity.getRulesJson());
                log.info("Loaded MA alert config from database: topLogic={}, rules={}",
                        topLogic, rules.size());
            } else {
                // First run: seed default rules preserving the legacy MA5/13/30/55 behavior.
                List<Rule> seed = defaultSeedRules();
                this.topLogic = "OR";
                this.rules = seed;
                persistConfig(this.topLogic, seed);
                log.info("No persisted MA alert config; seeded default: {} rules (MA5/13/30/55 day crossovers)", seed.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load MA alert config from database, using seed default: {}", e.getMessage());
            this.rules = defaultSeedRules();
        }
    }

    /**
     * Evaluates all rules for a stock at the current price.
     * Returns one MAEvent per matching rule (deduped by the triggering condition's
     * frequency+period+direction). Caller dispatches each to AlertCoordinator.
     *
     * @param stockKey  unique stock key
     * @param stockName stock display name
     * @param price     current real-time price
     * @param session   current trading session
     * @return list of MA alert events (empty if no rule matches)
     */
    public List<MAEvent> check(String stockKey, String stockName, double price, TradingSession session) {
        if (!session.isTrading()) return List.of();
        if (rules.isEmpty()) return List.of();

        List<MAEvent> fired = new ArrayList<>();
        for (Rule rule : rules) {
            MAEvent event = evaluateRule(rule, stockKey, stockName, price, session);
            if (event != null) fired.add(event);
        }
        return fired;
    }

    private MAEvent evaluateRule(Rule rule, String stockKey, String stockName,
                                 double price, TradingSession session) {
        if (rule.conditions == null || rule.conditions.isEmpty()) return null;

        boolean isAnd = "AND".equalsIgnoreCase(rule.logic);
        // Track which condition produced a crossover event this tick, and each
        // condition's current matching state.
        Condition triggered = null;
        boolean allStatesMatch = true;

        for (Condition c : rule.conditions) {
            double maValue = maFor(stockKey, c.frequency, c.maPeriod);
            if (Double.isNaN(maValue) || maValue <= 0) {
                // Insufficient data: for AND this rule cannot fire; for OR skip this condition.
                if (isAnd) return null;
                continue;
            }

            Direction crossover = crossoverDetector.checkCrossover(
                    stockKey, c.frequency, c.maPeriod, price, maValue);
            boolean stateMatches = stateMatches(c, price, maValue);

            if (crossover == c.direction) {
                // This condition's crossover event fired this tick.
                if (triggered == null) triggered = c;
            }

            if (!stateMatches) allStatesMatch = false;
        }

        if (triggered == null) return null;

        if (isAnd) {
            // Event + state confirmation: all other conditions must currently match.
            if (!allStatesMatch) return null;
        }
        // OR: any crossover event fires.

        return new MAEvent(stockKey, stockName, triggered.maPeriod, triggered.direction,
                price, maFor(stockKey, triggered.frequency, triggered.maPeriod),
                session, triggered.frequency);
    }

    private double maFor(String stockKey, String frequency, int period) {
        List<Double> closes = kLineService.getCloses(stockKey, frequency);
        if (closes == null || closes.isEmpty()) return Double.NaN;
        return MACalculator.calculateMA(closes, period);
    }

    private boolean stateMatches(Condition c, double price, double maValue) {
        if (Double.isNaN(maValue) || maValue <= 0) return false;
        return switch (c.direction) {
            case BREAK_UP -> price > maValue;
            case BREAK_DOWN -> price < maValue;
            default -> false;
        };
    }

    // ---- Config access (for the config API controller) ----

    public String getTopLogic() { return topLogic; }
    public List<Rule> getRules() { return rules; }

    public synchronized void updateConfig(String newTopLogic, List<Rule> newRules) {
        String logic = "AND".equalsIgnoreCase(newTopLogic) ? "AND" : "OR";
        this.topLogic = logic;
        this.rules = newRules == null ? List.of() : newRules;
        persistConfig(this.topLogic, this.rules);
        log.info("MA alert config updated and persisted: topLogic={}, rules={}",
                topLogic, rules.size());
    }

    private void persistConfig(String logic, List<Rule> rulesToPersist) {
        try {
            MAAlertConfigEntity entity = new MAAlertConfigEntity();
            entity.setId(CONFIG_ID);
            entity.setLogic(logic);
            entity.setRulesJson(objectMapper.writeValueAsString(rulesToPersist));
            configRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist MA alert config to database: {}", e.getMessage());
        }
    }

    private List<Rule> parseRules(String json) {
        try {
            List<Rule> parsed = objectMapper.readValue(json, new TypeReference<List<Rule>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            log.warn("Failed to parse persisted MA alert rules JSON, falling back to seed default: {}", e.getMessage());
            return defaultSeedRules();
        }
    }

    /** Seed: legacy MA5/13/30/55 day crossovers (both directions), OR-combined. */
    static List<Rule> defaultSeedRules() {
        List<Rule> seed = new ArrayList<>();
        for (int period : List.of(5, 13, 30, 55)) {
            seed.add(new Rule("日线MA" + period + "突破", "OR", List.of(
                    new Condition(FREQ_DAY, period, Direction.BREAK_UP),
                    new Condition(FREQ_DAY, period, Direction.BREAK_DOWN))));
        }
        return seed;
    }

    // ---- DTOs (Jackson-serialized) ----

    public static class Rule {
        @JsonProperty("name")
        public String name;
        @JsonProperty("logic")
        public String logic = "OR";
        @JsonProperty("conditions")
        public List<Condition> conditions = List.of();

        public Rule() {}
        public Rule(String name, String logic, List<Condition> conditions) {
            this.name = name;
            this.logic = logic;
            this.conditions = conditions;
        }
        public String getName() { return name; }
        public String getLogic() { return logic; }
        public List<Condition> getConditions() { return conditions; }
    }

    public static class Condition {
        @JsonProperty("frequency")
        public String frequency = FREQ_DAY;
        @JsonProperty("maPeriod")
        public int maPeriod;
        @JsonProperty("direction")
        public Direction direction;

        public Condition() {}
        public Condition(String frequency, int maPeriod, Direction direction) {
            this.frequency = frequency;
            this.maPeriod = maPeriod;
            this.direction = direction;
        }
        public String getFrequency() { return frequency; }
        public int getMaPeriod() { return maPeriod; }
        public Direction getDirection() { return direction; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Condition c)) return false;
            return maPeriod == c.maPeriod
                    && Objects.equals(frequency, c.frequency)
                    && direction == c.direction;
        }
        @Override
        public int hashCode() { return Objects.hash(frequency, maPeriod, direction); }
    }
}
