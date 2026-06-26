package com.autotrading.entity;

import jakarta.persistence.*;

/**
 * Persisted fluctuation monitoring rules (BF-2).
 * <p>
 * Singleton row (id is always 1). Holds the rule-combination logic plus the
 * rule list serialized as JSON. The runtime monitor loads this on startup and
 * overwrites it whenever the user saves new rules, so the dashboard always
 * reflects the latest persisted value after a refresh or restart.
 */
@Entity
@Table(name = "fluctuation_config")
public class FluctuationConfigEntity {

    /** Singleton primary key (always 1). */
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private String logic = "OR";

    /** JSON array of rules, e.g. [{"windowMinutes":3,"thresholdPercent":3.0}]. */
    @Column(nullable = false, length = 4096)
    private String rulesJson = "[]";

    public FluctuationConfigEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogic() { return logic; }
    public void setLogic(String logic) { this.logic = logic; }

    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
}
