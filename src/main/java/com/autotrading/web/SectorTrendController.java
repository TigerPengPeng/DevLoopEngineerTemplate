package com.autotrading.web;

import com.autotrading.market.SectorMappingService;
import com.autotrading.market.SectorTrendReportService.SectorTrendReport;
import com.autotrading.model.StockInfo;
import com.autotrading.monitor.SectorTrendReportScheduler;
import com.autotrading.startup.QuoteProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * API endpoints for sector trend reports and sector mapping.
 */
@RestController
public class SectorTrendController {

    private final SectorTrendReportScheduler scheduler;
    private final SectorMappingService mappingService;
    private final QuoteProcessor quoteProcessor;

    public SectorTrendController(SectorTrendReportScheduler scheduler,
                                  SectorMappingService mappingService,
                                  QuoteProcessor quoteProcessor) {
        this.scheduler = scheduler;
        this.mappingService = mappingService;
        this.quoteProcessor = quoteProcessor;
    }

    @GetMapping("/api/sector-trend/latest")
    public Map<String, Object> getLatestReport() {
        SectorTrendReport report = scheduler.getLatest();
        Map<String, Object> result = new LinkedHashMap<>();
        if (report == null) {
            result.put("status", "no_report");
            result.put("message", "尚未生成行业趋势报告");
        } else {
            result.put("status", "ok");
            result.put("report", report);
        }
        return result;
    }

    @GetMapping("/api/sector-trend/history")
    public Map<String, Object> getHistory() {
        List<SectorTrendReport> history = scheduler.getHistory();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (SectorTrendReport r : history) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("date", r.date());
            s.put("sectorCount", r.sectors().size());
            s.put("sentiment", r.overallSentiment());
            s.put("generatedAt", r.generatedAt());
            summaries.add(s);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", summaries.size());
        result.put("reports", summaries);
        return result;
    }

    @GetMapping("/api/sector-trend/{date}")
    public Map<String, Object> getByDate(@PathVariable String date) {
        SectorTrendReport report = scheduler.getByDate(date);
        Map<String, Object> result = new LinkedHashMap<>();
        if (report == null) {
            result.put("status", "not_found");
            result.put("message", "未找到 " + date + " 的报告");
        } else {
            result.put("status", "ok");
            result.put("report", report);
        }
        return result;
    }

    @PostMapping("/api/sector-trend/generate")
    public Map<String, Object> generateNow() {
        SectorTrendReport report = scheduler.generateAndSend(true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("report", report);
        return result;
    }

    @GetMapping("/api/sector-mapping")
    public Map<String, Object> getSectorMapping() {
        List<StockInfo> stocks = quoteProcessor.getStocks();
        Map<String, List<StockInfo>> grouped = mappingService.groupBySector(stocks);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> sectors = new LinkedHashMap<>();
        for (Map.Entry<String, List<StockInfo>> entry : grouped.entrySet()) {
            List<Map<String, Object>> stockList = new ArrayList<>();
            for (StockInfo s : entry.getValue()) {
                Map<String, Object> stock = new LinkedHashMap<>();
                stock.put("key", s.key());
                stock.put("name", s.getName());
                stockList.add(stock);
            }
            sectors.put(entry.getKey(), stockList);
        }
        result.put("sectors", sectors);
        result.put("totalSectors", grouped.size());
        return result;
    }
}
