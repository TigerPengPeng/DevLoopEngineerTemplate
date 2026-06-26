package com.autotrading.market;

import com.autotrading.model.StockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps stock codes/names to industry sectors using keyword matching.
 *
 * The mapping rules are keyword-based: if a stock name contains any of the
 * configured keywords for a sector, it is assigned to that sector.
 * A stock can belong to multiple sectors.
 *
 * Default sectors cover common categories like semiconductors, storage,
 * consumer electronics, etc. Users can extend via configuration.
 */
@Service
public class SectorMappingService {

    private static final Logger log = LoggerFactory.getLogger(SectorMappingService.class);

    /** sector name -> list of keyword patterns (case-insensitive, matches stock name). */
    private final Map<String, List<String>> sectorKeywords;

    /** explicit stockKey -> sector overrides (highest priority). */
    private final Map<String, String> explicitMappings;

    public SectorMappingService() {
        this.sectorKeywords = new LinkedHashMap<>();
        this.explicitMappings = new LinkedHashMap<>();
        initDefaults();
    }

    private void initDefaults() {
        addSector("半导体", List.of(
                "Semiconductor", "半导体", "Chip", "芯片", "TSMC", "台积电",
                "NVIDIA", "英伟达", "AMD", "Intel", "高通", "Qualcomm",
                "Broadcom", "博通", "Micron", "美光", "ASML", "AMAT", "LRCX",
                "Memory", "存储", "DRAM", "NAND", "Flash", "Hynix", "海力士",
                "Samsung", "三星"
        ));
        addSector("存储", List.of(
                "Micron", "美光", "Memory", "存储", "DRAM", "NAND", "Flash",
                "Hynix", "海力士", "Samsung", "三星", "SanDisk", "Western Digital",
                "西部数据", "Storage", "Seagate"
        ));
        addSector("消费电子", List.of(
                "Apple", "苹果", "Consumer", "消费", "Xiaomi", "小米",
                "Sony", "索尼", "Samsung", "三星"
        ));
        addSector("人工智能", List.of(
                "AI", "Artificial", "人工智能", "NVIDIA", "英伟达", "Palantir",
                "C3", "OpenAI", "Machine Learning", "机器学习",
                "Robotics", "机器人", "Automation"
        ));
        addSector("新能源", List.of(
                "Solar", "太阳能", "Battery", "电池", "新能源", "Clean Energy",
                "Tesla", "特斯拉", "EV", "Electric Vehicle", "电动车",
                "Lithium", "锂", "NIO", "蔚来", "BYD", "比亚迪"
        ));
        addSector("金融", List.of(
                "Bank", "银行", "Financial", "金融", "Insurance", "保险",
                "Securities", "证券", "Broker", "券商"
        ));
        addSector("医药", List.of(
                "Pharma", "制药", "Biotech", "生物", "Medical", "医疗",
                "Health", "健康", "Drug", "药品"
        ));
        addSector("通信", List.of(
                "Telecom", "电信", "Communication", "通信", "5G",
                "Network", "网络", "Cisco", "思科", "Huawei", "华为"
        ));
        addSector("能源", List.of(
                "Oil", "石油", "Gas", "天然气", "Energy", "能源",
                "Petroleum", "Crude", "Coal", "煤炭"
        ));
        addSector("ETF杠杆", List.of(
                "Bull", "Bear", "Leveraged", "杠杆", "3X", "2X", "Direxion",
                "Inverse", "做空"
        ));
    }

    public void addSector(String sector, List<String> keywords) {
        sectorKeywords.put(sector, keywords);
    }

    public void addExplicitMapping(String stockKey, String sector) {
        explicitMappings.put(stockKey, sector);
    }

    /**
     * Returns all sector names.
     */
    public Set<String> getAllSectors() {
        return sectorKeywords.keySet();
    }

    /**
     * Maps a stock to its sector(s).
     * Explicit mapping takes priority; otherwise keyword matching on the stock name.
     *
     * @return list of matching sectors (may be empty)
     */
    public List<String> mapToSectors(StockInfo stock) {
        // Check explicit override first
        String explicit = explicitMappings.get(stock.key());
        if (explicit != null) {
            return List.of(explicit);
        }

        String name = stock.getName() == null ? "" : stock.getName().toLowerCase();
        List<String> matched = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : sectorKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (name.contains(keyword.toLowerCase())) {
                    if (!matched.contains(entry.getKey())) {
                        matched.add(entry.getKey());
                    }
                    break;
                }
            }
        }

        return matched;
    }

    /**
     * Groups all stocks by their sector.
     *
     * @return sector name -> list of stocks in that sector
     */
    public Map<String, List<StockInfo>> groupBySector(List<StockInfo> stocks) {
        Map<String, List<StockInfo>> result = new LinkedHashMap<>();
        List<StockInfo> unmapped = new ArrayList<>();

        for (StockInfo stock : stocks) {
            List<String> sectors = mapToSectors(stock);
            if (sectors.isEmpty()) {
                unmapped.add(stock);
            } else {
                for (String sector : sectors) {
                    result.computeIfAbsent(sector, k -> new ArrayList<>()).add(stock);
                }
            }
        }

        if (!unmapped.isEmpty()) {
            result.put("其他", unmapped);
        }

        log.debug("Sector grouping: {} sectors from {} stocks", result.size(), stocks.size());
        return result;
    }
}
