package com.autotrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the Futu stock monitoring service.
 * Headless (non-web) application with scheduling enabled for periodic tasks.
 */
@SpringBootApplication
@EnableScheduling
public class AutoTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoTradingApplication.class, args);
    }
}
