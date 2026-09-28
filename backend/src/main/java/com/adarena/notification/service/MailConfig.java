package com.adarena.notification.service;

import com.adarena.common.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Elige cómo se entregan los emails:
 * <ul>
 *   <li>Con {@code BREVO_API_KEY}: por la API HTTPS de Brevo (la opción gratuita para la nube).</li>
 *   <li>Con {@code SMTP_HOST}: por SMTP (Resend, el correo de tu dominio…).</li>
 *   <li>Sin ninguno (en tu ordenador): se escriben en el log y no se envía nada a nadie.</li>
 * </ul>
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    MailDelivery mailDelivery(AppProperties properties, JsonMapper json) {
        AppProperties.Mail mail = properties.mail();
        if (mail.usesBrevo()) {
            log.info("Emails are sent through the Brevo API");
            return new BrevoMailDelivery(mail, json, BrevoMailDelivery.ENDPOINT);
        }
        if (!mail.isConfigured()) {
            log.warn("Neither BREVO_API_KEY nor SMTP_HOST is set: emails will only be written to the log");
            return new LoggingMailDelivery();
        }
        return new SmtpMailDelivery(mail);
    }

    static final class LoggingMailDelivery implements MailDelivery {

        @Override
        public void send(String to, String subject, String html, String text) {
            log.info("[EMAIL NOT SENT: no SMTP configured] To: {} | Subject: {}\n{}", to, subject, text);
        }

        @Override
        public boolean isReal() {
            return false;
        }
    }

    static final class SmtpMailDelivery implements MailDelivery {

        private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        private final InternetAddress from;

        SmtpMailDelivery(AppProperties.Mail mail) {
            try {
                this.from = new InternetAddress(mail.from(), true);
            } catch (Exception e) {
                throw new IllegalStateException("MAIL_FROM is not a valid address: " + mail.from(), e);
            }
            sender.setHost(mail.host());
            sender.setPort(mail.port());
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties props = sender.getJavaMailProperties();
            if (mail.username() != null && !mail.username().isBlank()) {
                sender.setUsername(mail.username());
                sender.setPassword(mail.password());
                props.put("mail.smtp.auth", "true");
            }
            if (mail.ssl()) {
                props.put("mail.smtp.ssl.enable", "true");        // puerto 465 (SSL directo)
            } else {
                props.put("mail.smtp.starttls.enable", "true");   // puerto 587 (STARTTLS)
                props.put("mail.smtp.starttls.required", "true");
            }
            // Nunca dejar un hilo colgado esperando a un servidor de correo lento
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");
        }

        @Override
        public void send(String to, String subject, String html, String text) throws Exception {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        }

        @Override
        public boolean isReal() {
            return true;
        }
    }
}
