package com.autotrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for Futu OpenD connection and monitoring parameters.
 */
@Configuration
@ConfigurationProperties(prefix = "futu")
public class FutuProperties {

    private Opend opend = new Opend();
    private Filter filter = new Filter();
    private Monitor monitor = new Monitor();
    private Reconnect reconnect = new Reconnect();

    public Opend getOpend() { return opend; }
    public void setOpend(Opend opend) { this.opend = opend; }
    public Filter getFilter() { return filter; }
    public void setFilter(Filter filter) { this.filter = filter; }
    public Monitor getMonitor() { return monitor; }
    public void setMonitor(Monitor monitor) { this.monitor = monitor; }
    public Reconnect getReconnect() { return reconnect; }
    public void setReconnect(Reconnect reconnect) { this.reconnect = reconnect; }

    public static class Opend {
        private String ip = "127.0.0.1";
        private int port = 11111;
        private boolean encrypt = false;
        private String rsaKey = "";

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isEncrypt() { return encrypt; }
        public void setEncrypt(boolean encrypt) { this.encrypt = encrypt; }
        public String getRsaKey() { return rsaKey; }
        public void setRsaKey(String rsaKey) { this.rsaKey = rsaKey; }
    }

    public static class Filter {
        private String groupName = "";

        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
    }

    public static class Monitor {
        private List<Integer> maPeriods = List.of(5, 13, 30, 55);
        private double priceChangeThreshold = 2.0;
        private int alertCooldownMinutes = 15;
        private long klineRefreshInterval = 60000;
        private long marketStatePollInterval = 30000;
        private long snapshotPollInterval = 10000;

        public List<Integer> getMaPeriods() { return maPeriods; }
        public void setMaPeriods(List<Integer> maPeriods) { this.maPeriods = maPeriods; }
        public double getPriceChangeThreshold() { return priceChangeThreshold; }
        public void setPriceChangeThreshold(double v) { this.priceChangeThreshold = v; }
        public int getAlertCooldownMinutes() { return alertCooldownMinutes; }
        public void setAlertCooldownMinutes(int v) { this.alertCooldownMinutes = v; }
        public long getKlineRefreshInterval() { return klineRefreshInterval; }
        public void setKlineRefreshInterval(long v) { this.klineRefreshInterval = v; }
        public long getMarketStatePollInterval() { return marketStatePollInterval; }
        public void setMarketStatePollInterval(long v) { this.marketStatePollInterval = v; }
        public long getSnapshotPollInterval() { return snapshotPollInterval; }
        public void setSnapshotPollInterval(long v) { this.snapshotPollInterval = v; }
    }

    public static class Reconnect {
        private long initialDelayMs = 5000;
        private long maxDelayMs = 60000;
        private double multiplier = 2.0;

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long v) { this.initialDelayMs = v; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long v) { this.maxDelayMs = v; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double v) { this.multiplier = v; }
    }
}
