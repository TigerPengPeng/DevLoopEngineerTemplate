package com.autotrading.web;

import com.autotrading.startup.ApplicationStartupRunner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Triggers a full stock list reload from Futu OpenD.
 */
@RestController
public class StockRefreshController {

    private final ApplicationStartupRunner startupRunner;

    public StockRefreshController(ApplicationStartupRunner startupRunner) {
        this.startupRunner = startupRunner;
    }

    @PostMapping("/api/refresh-stocks")
    public Map<String, Object> refreshStocks() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            int count = startupRunner.reloadStocks();
            result.put("status", count > 0 ? "ok" : "warning");
            result.put("stockCount", count);
            result.put("message", count > 0
                    ? "Reloaded " + count + " stocks from Futu OpenD"
                    : "No stocks found");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        result.put("timestamp", Instant.now().toString());
        return result;
    }
}
