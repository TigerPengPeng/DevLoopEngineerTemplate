package com.autotrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for email notification.
 */
@Configuration
@ConfigurationProperties(prefix = "notification.mail")
public class NotificationProperties {

    private List<String> to = List.of();
    private String from = "";
    private boolean enabled = true;

    public List<String> getTo() { return to; }
    public void setTo(List<String> to) { this.to = to; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
