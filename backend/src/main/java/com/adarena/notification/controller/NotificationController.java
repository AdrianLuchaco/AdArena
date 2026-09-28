package com.adarena.notification.controller;

import com.adarena.notification.dto.NotificationsResponse;
import com.adarena.notification.service.NotificationService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Avisos")
@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Tus avisos (los más recientes primero) y cuántos tienes sin leer")
    @GetMapping
    public NotificationsResponse list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(CurrentUser.id(jwt));
    }

    @Operation(summary = "Marcar todos tus avisos como leídos")
    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marcar un aviso como leído")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        notificationService.markRead(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
