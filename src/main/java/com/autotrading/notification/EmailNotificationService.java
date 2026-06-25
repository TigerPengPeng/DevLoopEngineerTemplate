package com.autotrading.notification;

import com.autotrading.config.NotificationProperties;
import com.autotrading.model.MAEvent;
import com.autotrading.model.PriceAlert;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

/**
 * Sends alert emails via Spring Mail (JavaMail).
 * Failures are logged but do not interrupt the monitoring flow.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    private volatile boolean configured = false;

    public EmailNotificationService(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @PostConstruct
    public void checkConfiguration() {
        if (!properties.isEnabled()) {
            log.warn("Email notifications disabled by configuration");
            configured = false;
            return;
        }
        if (properties.getTo().isEmpty()) {
            log.warn("No email recipients configured (notification.mail.to is empty)");
            configured = false;
            return;
        }
        configured = true;
        log.info("Email notifications enabled for {} recipients", properties.getTo().size());
    }

    public void sendMAEventAlert(MAEvent event) {
        if (!configured) {
            log.debug("Email not configured, skipping MA alert for {}", event.getStockKey());
            return;
        }
        String subject = NotificationTemplate.maEventSubject(event);
        String body = NotificationTemplate.maEventBody(event);
        sendHtml(subject, body);
    }

    public void sendPriceAlert(PriceAlert alert) {
        if (!configured) {
            log.debug("Email not configured, skipping price alert for {}", alert.getStockKey());
            return;
        }
        String subject = NotificationTemplate.priceAlertSubject(alert);
        String body = NotificationTemplate.priceAlertBody(alert);
        sendHtml(subject, body);
    }

    private void sendHtml(String subject, String htmlBody) {
        List<String> recipients = properties.getTo();
        for (String to : recipients) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(message);
                log.info("Email sent (to={}, subject={})", to, subject);
            } catch (Exception e) {
                log.error("Email send failed (to={}, subject={}): {}", to, subject, e.getMessage());
            }
        }
    }

    public boolean isConfigured() {
        return configured;
    }
}
