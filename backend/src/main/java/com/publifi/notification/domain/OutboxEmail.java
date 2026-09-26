package com.publifi.notification.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * Email pendiente de envío (patrón outbox). Se inserta en la misma transacción que el
 * evento que lo provoca y un proceso programado lo envía con reintentos.
 */
@Entity
@Table(name = "email_outbox")
public class OutboxEmail extends BaseEntity {

    public static final int MAX_ATTEMPTS = 5;

    private String toEmail;
    private String subject;
    private String htmlBody;
    private String textBody;

    @Enumerated(EnumType.STRING)
    private OutboxEmailStatus status;

    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private String dedupKey;

    @CreationTimestamp
    private Instant createdAt;

    private Instant sentAt;

    protected OutboxEmail() {
        // JPA
    }

    public OutboxEmail(String toEmail, String subject, String htmlBody, String textBody,
                       String dedupKey, Instant now) {
        this.toEmail = toEmail;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
        this.dedupKey = dedupKey;
        this.status = OutboxEmailStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public void markSent(Instant now) {
        this.status = OutboxEmailStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    /** Reintento con espera exponencial: 1, 2, 4, 8 minutos... hasta MAX_ATTEMPTS. */
    public void markAttemptFailed(String error, Instant now) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= MAX_ATTEMPTS) {
            this.status = OutboxEmailStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plus(Duration.ofMinutes(1L << (attempts - 1)));
        }
    }

    public String getToEmail() {
        return toEmail;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public OutboxEmailStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
