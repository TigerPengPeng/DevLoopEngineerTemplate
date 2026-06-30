package com.autotrading.monitor;

import com.autotrading.entity.MAAlertConfigEntity;
import com.autotrading.indicator.CrossoverDetector;
import com.autotrading.market.KLineService;
import com.autotrading.model.Direction;
import com.autotrading.model.MAEvent;
import com.autotrading.model.TradingSession;
import com.autotrading.repository.MAAlertConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MARuleEngineTest {

    private KLineService kLineService;
    private CrossoverDetector detector;
    private MAAlertConfigRepository repo;
    private ObjectMapper mapper;
    private MARuleEngine engine;

    @BeforeEach
    void setUp() {
        kLineService = mock(KLineService.class);
        detector = new CrossoverDetector();
        repo = mock(MAAlertConfigRepository.class);
        mapper = new ObjectMapper();
        when(repo.findById(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        engine = new MARuleEngine(kLineService, detector, repo, mapper);
        engine.loadFromDatabase();
    }

    private void stubCloses(String freq, List<Double> closes) {
        when(kLineService.getCloses(anyString(), eq(freq))).thenReturn(closes);
    }

    @Test
    @DisplayName("Empty rules produce no events")
    void testEmptyRules() {
        engine.updateConfig("OR", List.of());
        List<MAEvent> events = engine.check("11.AAPL", "Apple", 150.0, TradingSession.REGULAR);
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("OR rule: single condition crossover fires event")
    void testOrRuleCrossoverFires() {
        stubCloses("day", List.of(140.0, 142.0, 141.0, 143.0, 145.0));

        MARuleEngine.Rule rule = new MARuleEngine.Rule("test", "OR",
                List.of(new MARuleEngine.Condition("day", 5, Direction.BREAK_UP)));
        engine.updateConfig("OR", List.of(rule));

        // First tick: establishes state (price above MA5=142.2), no event
        engine.check("11.AAPL", "Apple", 150.0, TradingSession.REGULAR);
        // Second tick still above, no crossover
        List<MAEvent> e2 = engine.check("11.AAPL", "Apple", 151.0, TradingSession.REGULAR);
        assertTrue(e2.isEmpty());

        // Now price drops below MA5 -> BREAK_DOWN event. But rule wants BREAK_UP, so no fire.
        detector.resetState("11.AAPL");
        // re-seed state as below
        stubCloses("day", List.of(150.0, 149.0, 148.0, 147.0, 146.0)); // MA5 = 148.0
        List<MAEvent> e3 = engine.check("11.AAPL", "Apple", 145.0, TradingSession.REGULAR);
        assertTrue(e3.isEmpty(), "BREAK_DOWN when rule wants BREAK_UP should not fire");

        // Price rises above MA5 -> BREAK_UP crossover -> fires
        List<MAEvent> e4 = engine.check("11.AAPL", "Apple", 150.0, TradingSession.REGULAR);
        assertEquals(1, e4.size());
        assertEquals(Direction.BREAK_UP, e4.get(0).getDirection());
        assertEquals("day", e4.get(0).getFrequency());
    }

    @Test
    @DisplayName("AND rule: crossover fires only when all other conditions match state")
    void testAndRuleEventPlusState() {
        // Daily MA5 = 145.0, Weekly MA13 = 140.0
        stubCloses("day", List.of(140.0, 142.0, 144.0, 146.0, 148.0)); // MA5 = 144.0
        stubCloses("week", List.of(130.0, 135.0, 138.0, 142.0, 145.0, 140.0, 138.0,
                135.0, 132.0, 130.0, 128.0, 126.0, 140.0)); // MA13 = 133.69

        MARuleEngine.Rule rule = new MARuleEngine.Rule("and-test", "AND", List.of(
                new MARuleEngine.Condition("day", 5, Direction.BREAK_UP),
                new MARuleEngine.Condition("week", 13, Direction.BREAK_UP)));
        engine.updateConfig("OR", List.of(rule));

        // Seed daily state as below MA5 (144.0): price 140 is below
        engine.check("11.AAPL", "Apple", 140.0, TradingSession.REGULAR);
        // Price crosses above daily MA5 (144.0) -> BREAK_UP event.
        // Weekly: price 150 > weekly MA13 (133.69) -> state matches BREAK_UP.
        // AND rule should fire.
        List<MAEvent> fired = engine.check("11.AAPL", "Apple", 150.0, TradingSession.REGULAR);
        assertEquals(1, fired.size(), "AND rule should fire: daily crossover + weekly state matches");
        assertEquals("day", fired.get(0).getFrequency());
    }

    @Test
    @DisplayName("AND rule does NOT fire when other condition state doesn't match")
    void testAndRuleStateMismatch() {
        stubCloses("day", List.of(140.0, 142.0, 144.0, 146.0, 148.0)); // MA5 = 144.0
        // Weekly MA13 very high so price is below it
        stubCloses("week", List.of(200.0, 195.0, 190.0, 185.0, 180.0, 175.0, 170.0,
                165.0, 160.0, 155.0, 150.0, 145.0, 140.0)); // MA13 = 170.0

        MARuleEngine.Rule rule = new MARuleEngine.Rule("and-mismatch", "AND", List.of(
                new MARuleEngine.Condition("day", 5, Direction.BREAK_UP),
                new MARuleEngine.Condition("week", 13, Direction.BREAK_UP)));
        engine.updateConfig("OR", List.of(rule));

        // Seed daily state below MA5(144.0)
        engine.check("11.AAPL", "Apple", 140.0, TradingSession.REGULAR);
        // Price crosses above daily MA5 -> BREAK_UP event. But weekly: price 150 < MA13(170.0),
        // state does NOT match BREAK_UP. AND rule must NOT fire.
        List<MAEvent> fired = engine.check("11.AAPL", "Apple", 150.0, TradingSession.REGULAR);
        assertTrue(fired.isEmpty(), "AND rule must not fire when weekly state doesn't match");
    }

    @Test
    @DisplayName("Default seed rules preserve MA5/13/30/55 day crossovers")
    void testDefaultSeedRules() {
        List<MARuleEngine.Rule> seed = MARuleEngine.defaultSeedRules();
        assertEquals(4, seed.size());
        assertEquals("OR", seed.get(0).getLogic());
        // Each seed rule has both BREAK_UP and BREAK_DOWN conditions
        for (MARuleEngine.Rule r : seed) {
            assertEquals(2, r.getConditions().size());
        }
    }

    @Test
    @DisplayName("Non-trading session produces no events")
    void testNonTradingSession() {
        stubCloses("day", List.of(140.0, 142.0, 141.0, 143.0, 145.0));
        MARuleEngine.Rule rule = new MARuleEngine.Rule("test", "OR",
                List.of(new MARuleEngine.Condition("day", 5, Direction.BREAK_UP)));
        engine.updateConfig("OR", List.of(rule));

        List<MAEvent> events = engine.check("11.AAPL", "Apple", 150.0, TradingSession.CLOSED);
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("Frequency distinguishes day vs week state independently")
    void testFrequencyIndependentState() {
        stubCloses("day", List.of(140.0, 142.0, 141.0, 143.0, 145.0)); // MA5 = 142.2
        stubCloses("week", List.of(130.0, 135.0, 140.0, 145.0, 150.0)); // MA5 = 140.0

        MARuleEngine.Rule dayRule = new MARuleEngine.Rule("day", "OR",
                List.of(new MARuleEngine.Condition("day", 5, Direction.BREAK_UP)));
        MARuleEngine.Rule weekRule = new MARuleEngine.Rule("week", "OR",
                List.of(new MARuleEngine.Condition("week", 5, Direction.BREAK_UP)));
        engine.updateConfig("OR", List.of(dayRule, weekRule));

        // Seed both as below
        engine.check("11.AAPL", "Apple", 139.0, TradingSession.REGULAR);

        // Price crosses above both daily MA5(142.2) and weekly MA5(140.0) -> both fire
        List<MAEvent> fired = engine.check("11.AAPL", "Apple", 143.0, TradingSession.REGULAR);
        assertEquals(2, fired.size());
        List<String> freqs = fired.stream().map(MAEvent::getFrequency).toList();
        assertTrue(freqs.contains("day"));
        assertTrue(freqs.contains("week"));
    }
}
