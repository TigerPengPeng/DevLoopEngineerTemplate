package com.autotrading.notification;

import com.autotrading.config.NotificationProperties;
import com.autotrading.market.RiskAssessmentService;
import com.autotrading.model.MAEvent;
import com.autotrading.model.PriceAlert;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

/**
 * Sends alert emails via Spring Mail (JavaMail).
 * Uses async dispatch so SMTP latency never blocks the monitoring thread.
 * Failures are logged with full stack traces but do not interrupt monitoring.
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
        log.info("Email notifications enabled for {} recipients: {}", properties.getTo().size(), properties.getTo());
    }

    @Async("emailExecutor")
    public void sendMAEventAlert(MAEvent event) {
        if (!configured) return;
        String subject = NotificationTemplate.maEventSubject(event);
        String body = NotificationTemplate.maEventBody(event);
        sendHtml(subject, body);
    }

    @Async("emailExecutor")
    public void sendPriceAlert(PriceAlert alert) {
        if (!configured) return;
        String subject = NotificationTemplate.priceAlertSubject(alert);
        String body = NotificationTemplate.priceAlertBody(alert);
        sendHtml(subject, body);
    }

    @Async("emailExecutor")
    public void sendRiskReport(String subject, String marketLabel, String dateStr,
                              List<RiskAssessmentService.RiskAssessment> assessments) {
        if (!configured) return;
        List<NotificationTemplate.RiskReportItem> items = assessments.stream()
                .map(a -> new NotificationTemplate.RiskReportItem(
                        a.stockKey(), a.stockName(), a.score(),
                        a.level() == RiskAssessmentService.RiskLevel.HIGH,
                        a.changeRate(), a.riskFactors()))
                .toList();
        String body = NotificationTemplate.riskReportBody(marketLabel, dateStr, items);
        sendHtml(subject, body);
    }

    private void sendHtml(String subject, String htmlBody) {
        List<String> recipients = properties.getTo();
        log.info("Sending email to {} recipient(s): {}", recipients.size(), recipients);
        for (String to : recipients) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(properties.getFrom());
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(message);
                log.info("Email sent successfully (to={}, subject={})", to, subject);
            } catch (Throwable t) {
                // Catch Throwable (not just Exception): classpath mismatches can
                // throw NoSuchMethodError / NoClassDefFoundError which extend Error
                log.error("Email send FAILED (to={}, subject={}): {}", to, subject, t.getMessage(), t);
            }
        }
    }

    public boolean isConfigured() {
        return configured;
    }
}
