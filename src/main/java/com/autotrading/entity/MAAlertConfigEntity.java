package com.autotrading.entity;

import jakarta.persistence.*;

/**
 * Persisted MA alert rules (replaces the hardcoded MA5/13/30/55 crossover alerts).
 * <p>
 * Singleton row (id is always 1). Holds the rule-combination logic plus the
 * rule list serialized as JSON. {@link com.autotrading.monitor.MARuleEngine}
 * loads this on startup and overwrites it whenever the user saves new rules,
 * so the dashboard always reflects the latest persisted value after a refresh
 * or restart.
 */
@Entity
@Table(name = "ma_alert_config")
public class MAAlertConfigEntity {

    /** Singleton primary key (always 1). */
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private String logic = "OR";

    /** JSON array of rules, e.g. [{"name":"...","logic":"OR","conditions":[...]}]. */
    @Column(nullable = false, length = 8192)
    private String rulesJson = "[]";

    public MAAlertConfigEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogic() { return logic; }
    public void setLogic(String logic) { this.logic = logic; }

    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
}
