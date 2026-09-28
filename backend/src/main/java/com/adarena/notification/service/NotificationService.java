package com.adarena.notification.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.notification.domain.Notification;
import com.adarena.notification.domain.OutboxEmail;
import com.adarena.notification.dto.NotificationsResponse;
import com.adarena.notification.event.UserNotificationEvent;
import com.adarena.notification.repository.NotificationRepository;
import com.adarena.notification.repository.OutboxEmailRepository;
import com.adarena.notification.service.EmailTemplates.EmailContent;
import com.adarena.realtime.ArenaNotification;
import com.adarena.user.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Avisos a los usuarios. Se llama SIEMPRE dentro de la transacción del caso de uso (pujar,
 * cerrar la ronda, moderar…), así que:
 * <ul>
 *   <li>si la operación se deshace, el aviso y el email tampoco existen (patrón "outbox");</li>
 *   <li>el aviso al instante por WebSocket sale solo después del commit;</li>
 *   <li>el email lo envía después {@link OutboxEmailSender}, con reintentos si el SMTP falla.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final int MAX_LIST = 50;

    private final NotificationRepository notificationRepository;
    private final OutboxEmailRepository outboxRepository;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, OutboxEmailRepository outboxRepository,
                               AppProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** Aviso en la campana + aviso al instante + (si el aviso lo pide) email. */
    @Transactional
    public void notify(User user, Notice notice) {
        if (notice.email()) {
            outboxRepository.save(email(user, notice, null));
        }
        store(user, notice);
        events.publishEvent(new UserNotificationEvent(user.getId(),
                new ArenaNotification(notice.type().name(), notice.title(), notice.body(), notice.link())));
    }

    /**
     * Como {@link #notify}, pero como mucho UNA vez por {@code dedupKey}. Se usa para "te han
     * superado": en una guerra de pujas no queremos mandar diez emails. No empuja nada por
     * WebSocket (de eso ya se encarga el evento de la puja, que avisa siempre).
     *
     * @return false si ya se había avisado con esa clave
     */
    @Transactional
    public boolean notifyOnce(User user, Notice notice, String dedupKey) {
        EmailContent content = EmailTemplates.notice(notice, user.getDisplayName(), properties.webUrl());
        int inserted = outboxRepository.insertIfAbsent(UUID.randomUUID(), user.getEmail(), content.subject(),
                content.html(), content.text(), dedupKey, clock.instant());
        if (inserted == 0) {
            return false;
        }
        store(user, notice);
        return true;
    }

    /** Solo email, sin aviso en la campana (p. ej. "cambia tu contraseña"). */
    @Transactional
    public void sendEmail(String toEmail, EmailContent content) {
        outboxRepository.save(new OutboxEmail(toEmail, content.subject(), content.html(), content.text(), null,
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public NotificationsResponse list(UUID userId) {
        return new NotificationsResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId),
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(MAX_LIST)).stream()
                        .map(NotificationsResponse.Item::from)
                        .toList());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "That notification doesn't exist."))
                .markRead(clock.instant());
    }

    private void store(User user, Notice notice) {
        notificationRepository.save(new Notification(user.getId(), notice.type(), notice.title(), notice.body(),
                notice.link()));
    }

    private OutboxEmail email(User user, Notice notice, String dedupKey) {
        EmailContent content = EmailTemplates.notice(notice, user.getDisplayName(), properties.webUrl());
        Instant now = clock.instant();
        return new OutboxEmail(user.getEmail(), content.subject(), content.html(), content.text(), dedupKey, now);
    }
}
