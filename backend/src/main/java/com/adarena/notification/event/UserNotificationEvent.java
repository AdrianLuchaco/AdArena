package com.adarena.notification.event;

import com.adarena.realtime.ArenaNotification;

import java.util.UUID;

/**
 * Un aviso nuevo para un usuario. Se publica DENTRO de la transacción y se envía por WebSocket
 * (a su canal privado) solo DESPUÉS del commit.
 */
public record UserNotificationEvent(UUID userId, ArenaNotification notification) {
}
