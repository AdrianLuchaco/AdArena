package com.adarena.notification.dto;

import com.adarena.notification.domain.Notification;
import com.adarena.notification.domain.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tus avisos (los 50 más recientes) y cuántos tienes sin leer. */
public record NotificationsResponse(long unreadCount, List<Item> items) {

    public record Item(UUID id, NotificationType type, String title, String body, String link, Instant createdAt,
                       boolean read) {

        public static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getBody(), notification.getLink(), notification.getCreatedAt(),
                    notification.getReadAt() != null);
        }
    }
}
