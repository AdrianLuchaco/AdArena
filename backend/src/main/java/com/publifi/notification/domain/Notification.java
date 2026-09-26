package com.publifi.notification.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Notificación que el usuario ve en su panel (campana). */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;
    private String body;
    private String link;
    private Instant readAt;

    @CreationTimestamp
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID userId, NotificationType type, String title, String body, String link) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
