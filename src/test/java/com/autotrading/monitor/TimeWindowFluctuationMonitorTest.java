package com.autotrading.monitor;

import com.autotrading.config.FutuProperties;
import com.autotrading.entity.FluctuationConfigEntity;
import com.autotrading.repository.FluctuationConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BF-2: fluctuation rules must persist to the database and reload from it.
 */
class TimeWindowFluctuationMonitorTest {

    private FluctuationConfigRepository repository;
    private FutuProperties properties;
    private TimeWindowFluctuationMonitor monitor;

    @BeforeEach
    void setUp() {
        repository = mock(FluctuationConfigRepository.class);
        properties = new FutuProperties();
        monitor = new TimeWindowFluctuationMonitor(properties, repository, new ObjectMapper());
    }

    private FutuProperties.Fluctuation rules(String logic, int w1, double t1, int w2, double t2) {
        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic(logic);
        cfg.setRules(List.of(new FutuProperties.FluctuationRule(w1, t1),
                             new FutuProperties.FluctuationRule(w2, t2)));
        cfg.setEvalIntervalMs(30000);
        return cfg;
    }

    @Test
    @DisplayName("BF-2: updateConfig persists logic + rules to database and updates memory")
    void updateConfigPersists() {
        FutuProperties.Fluctuation cfg = rules("OR", 3, 3.0, 5, 5.0);

        monitor.updateConfig(cfg);

        ArgumentCaptor<FluctuationConfigEntity> captor =
                ArgumentCaptor.forClass(FluctuationConfigEntity.class);
        verify(repository).save(captor.capture());
        FluctuationConfigEntity saved = captor.getValue();
        assertEquals(1L, saved.getId());
        assertEquals("OR", saved.getLogic());
        assertEquals("[{\"windowMinutes\":3,\"thresholdPercent\":3.0},{\"windowMinutes\":5,\"thresholdPercent\":5.0}]",
                saved.getRulesJson());

        // In-memory config is the updated one.
        assertEquals("OR", monitor.getConfig().getLogic());
        assertEquals(2, monitor.getConfig().getRules().size());
    }

    @Test
    @DisplayName("BF-2: loadFromDatabase restores persisted rules on startup")
    void loadFromDatabaseRestores() {
        FluctuationConfigEntity entity = new FluctuationConfigEntity();
        entity.setId(1L);
        entity.setLogic("AND");
        entity.setRulesJson("[{\"windowMinutes\":5,\"thresholdPercent\":5.0}]");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        monitor.loadFromDatabase();

        FutuProperties.Fluctuation cfg = monitor.getConfig();
        assertEquals("AND", cfg.getLogic());
        assertEquals(1, cfg.getRules().size());
        assertEquals(5, cfg.getRules().get(0).getWindowMinutes());
        assertEquals(5.0, cfg.getRules().get(0).getThresholdPercent());
    }

    @Test
    @DisplayName("BF-2: loadFromDatabase seeds default rules when database is empty")
    void loadFromDatabaseSeedsDefault() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        monitor.loadFromDatabase();

        // Default seed: 3min>=3% OR 5min>=5%
        FutuProperties.Fluctuation cfg = monitor.getConfig();
        assertEquals("OR", cfg.getLogic());
        assertEquals(2, cfg.getRules().size());
        assertEquals(3, cfg.getRules().get(0).getWindowMinutes());
        assertEquals(3.0, cfg.getRules().get(0).getThresholdPercent());
        assertEquals(5, cfg.getRules().get(1).getWindowMinutes());
        assertEquals(5.0, cfg.getRules().get(1).getThresholdPercent());

        ArgumentCaptor<FluctuationConfigEntity> captor =
                ArgumentCaptor.forClass(FluctuationConfigEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("OR", captor.getValue().getLogic());
    }

    @Test
    @DisplayName("BF-2: empty rules persist as [] and reload does not fall back to default")
    void emptyRulesPersistWithoutDefaultFallback() {
        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic("OR");
        cfg.setRules(List.of());
        cfg.setEvalIntervalMs(30000);

        monitor.updateConfig(cfg);

        ArgumentCaptor<FluctuationConfigEntity> captor =
                ArgumentCaptor.forClass(FluctuationConfigEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("[]", captor.getValue().getRulesJson());
        assertEquals(0, monitor.getConfig().getRules().size());

        // Reloading an explicitly-empty row must NOT re-seed the default rules.
        FluctuationConfigEntity emptyEntity = new FluctuationConfigEntity();
        emptyEntity.setId(1L);
        emptyEntity.setLogic("OR");
        emptyEntity.setRulesJson("[]");
        when(repository.findById(1L)).thenReturn(Optional.of(emptyEntity));

        monitor.loadFromDatabase();

        assertEquals(0, monitor.getConfig().getRules().size());
    }

    @Test
    @DisplayName("OR logic: stock qualifies when any rule matches")
    void evaluateOrLogicQualifies() {
        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic("OR");
        cfg.setRules(List.of(new FutuProperties.FluctuationRule(3, 3.0),
                             new FutuProperties.FluctuationRule(5, 5.0)));
        cfg.setEvalIntervalMs(30000);
        monitor.updateConfig(cfg);

        // +4% within 3 min -> rule1 matches; 5 min rule needs >=5% so it does not.
        monitor.recordPrice("11.AAPL", 100.0, 0L);
        monitor.recordPrice("11.AAPL", 100.0, 60_000L);
        monitor.recordPrice("11.AAPL", 104.0, 180_000L);

        TimeWindowFluctuationMonitor.StockFluctuationResult result =
                monitor.evaluate("11.AAPL", "Apple");

        assertNotNull(result);
        assertEquals(1, result.matchedRules().size());
        assertEquals("涨", result.direction());
    }

    @Test
    @DisplayName("AND logic: stock does not qualify unless every rule matches")
    void evaluateAndLogicRequiresAll() {
        FutuProperties.Fluctuation cfg = new FutuProperties.Fluctuation();
        cfg.setLogic("AND");
        cfg.setRules(List.of(new FutuProperties.FluctuationRule(3, 3.0),
                             new FutuProperties.FluctuationRule(5, 5.0)));
        cfg.setEvalIntervalMs(30000);
        monitor.updateConfig(cfg);

        // +4% matches rule1 but not rule2 (>=5%); AND therefore does not qualify.
        monitor.recordPrice("11.AAPL", 100.0, 0L);
        monitor.recordPrice("11.AAPL", 100.0, 60_000L);
        monitor.recordPrice("11.AAPL", 104.0, 180_000L);

        assertNull(monitor.evaluate("11.AAPL", "Apple"));
    }
}
