package com.autotrading;

import com.autotrading.monitor.ErrorLogAppender;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring Boot entry point for the Futu stock monitoring service.
 * Headless application with scheduling + async email dispatch enabled.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AutoTradingApplication {

    public static void main(String[] args) {
        // Register the in-memory ERROR log capture appender before Spring starts
        ErrorLogAppender.register();
        SpringApplication.run(AutoTradingApplication.class, args);
    }

    /**
     * Dedicated thread pool for email sending so SMTP latency never
     * blocks the monitoring/scheduling thread.
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
