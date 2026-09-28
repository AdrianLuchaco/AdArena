package com.adarena.notification.service;

import com.adarena.notification.domain.OutboxEmail;
import com.adarena.notification.repository.OutboxEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Envía los emails de la bandeja de salida (tabla email_outbox). Lo ejecuta cada pocos
 * segundos una tarea programada. Si el SMTP falla, el email se reintenta más tarde (1, 2, 4, 8
 * minutos) y, tras 5 intentos, queda como FAILED para que lo veas en el panel de administración.
 */
@Component
public class OutboxEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxEmailSender.class);
    private static final int BATCH_SIZE = 20;

    private final OutboxEmailRepository repository;
    private final MailDelivery delivery;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OutboxEmailSender(OutboxEmailRepository repository, MailDelivery delivery,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.delivery = delivery;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Envía una tanda de emails pendientes. Devuelve cuántos ha procesado. */
    public int sendDueEmails() {
        Integer processed = transaction.execute(status -> {
            Instant now = clock.instant();
            List<OutboxEmail> batch = repository.lockDueBatch(now, BATCH_SIZE);
            for (OutboxEmail email : batch) {
                try {
                    delivery.send(email.getToEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody());
                    email.markSent(clock.instant());
                } catch (Exception e) {
                    log.warn("Email {} to {} failed (attempt {}): {}", email.getId(), email.getToEmail(),
                            email.getAttempts() + 1, e.getMessage());
                    email.markAttemptFailed(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant());
                }
            }
            return batch.size();
        });
        return processed == null ? 0 : processed;
    }
}
