package com.autotrading.web;

import com.autotrading.monitor.ErrorLogAppender;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves captured ERROR-level logs for the dashboard error log viewer.
 */
@RestController
public class ErrorLogController {

    @GetMapping("/api/error-logs")
    public Map<String, Object> getErrorLogs(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<ErrorLogAppender.ErrorLogEntry> logs = ErrorLogAppender.getEntries(limit);
        result.put("count", logs.size());
        result.put("total", ErrorLogAppender.getEntryCount());
        result.put("logs", logs);
        return result;
    }

    @DeleteMapping("/api/error-logs")
    public Map<String, Object> clearErrorLogs() {
        ErrorLogAppender.clear();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "cleared");
        return result;
    }
}
