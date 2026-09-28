package com.adarena.admin.controller;

import com.adarena.admin.dto.AdminDtos;
import com.adarena.admin.service.AdminOverviewService;
import com.adarena.adslot.dto.AdminAdSlotView;
import com.adarena.adslot.service.AdSlotQueryService;
import com.adarena.adslot.service.ModerationService;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.service.PromotionService;
import com.adarena.security.CurrentUser;
import com.adarena.settings.dto.SettingsDto;
import com.adarena.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Panel de administración. Doble protección: SecurityConfig exige el rol ADMIN para
 * /api/admin/** y, además, cada método lo vuelve a comprobar con @PreAuthorize.
 */
@Tag(name = "Administración", description = "Solo para administradores")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminOverviewService overviewService;
    private final AdSlotQueryService adSlotQueryService;
    private final ModerationService moderationService;
    private final PromotionService promotionService;
    private final SettingsService settingsService;

    public AdminController(AdminOverviewService overviewService, AdSlotQueryService adSlotQueryService,
                           ModerationService moderationService, PromotionService promotionService,
                           SettingsService settingsService) {
        this.overviewService = overviewService;
        this.adSlotQueryService = adSlotQueryService;
        this.moderationService = moderationService;
        this.promotionService = promotionService;
        this.settingsService = settingsService;
    }

    @Operation(summary = "Resumen: puntos, pendientes y comprobación de las cuentas")
    @GetMapping("/overview")
    public AdminDtos.Overview overview() {
        return overviewService.overview();
    }

    // ------------------------------------------------------------------ moderación

    @Operation(summary = "Anuncios ganadores pendientes de moderar")
    @GetMapping("/ad-slots/pending")
    public List<AdminAdSlotView> pendingAdSlots() {
        return adSlotQueryService.pending();
    }

    @Operation(summary = "Últimas decisiones de moderación")
    @GetMapping("/ad-slots/recent")
    public List<AdminAdSlotView> recentAdSlots() {
        return adSlotQueryService.recent();
    }

    @Operation(summary = "Volver a leer la web del ganador (su presentación) antes de aprobarlo",
            description = "La lectura va en segundo plano: vuelve a pedir la lista en unos segundos.")
    @PostMapping("/ad-slots/{id}/showcase/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void refreshShowcase(@PathVariable UUID id) {
        adSlotQueryService.refreshShowcase(id);
    }

    @Operation(summary = "Aprobar: se gastan sus puntos, recibe su premio y el anuncio sale en portada")
    @PostMapping("/ad-slots/{id}/approve")
    public void approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        moderationService.approve(id, CurrentUser.id(jwt));
    }

    @Operation(summary = "Rechazar: se devuelve el 100 % y pasa el siguiente clasificado")
    @PostMapping("/ad-slots/{id}/reject")
    public ModerationService.RejectResult reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                 @Valid @RequestBody AdminDtos.RejectRequest request) {
        return moderationService.reject(id, CurrentUser.id(jwt), request.reason());
    }

    // ------------------------------------------------------------------ promociones (Créditos extra)

    @Operation(summary = "Promociones: las ocultas y las más denunciadas primero")
    @GetMapping("/tasks")
    public List<EarnDtos.AdminTask> tasks() {
        return promotionService.listForAdmin();
    }

    @Operation(summary = "Ocultar una promoción (se avisa a su dueño con el motivo)")
    @PostMapping("/tasks/{id}/hide")
    public void hideTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                         @Valid @RequestBody AdminDtos.RejectRequest request) {
        promotionService.hide(CurrentUser.id(jwt), id, request.reason());
    }

    @Operation(summary = "Volver a publicar una promoción oculta (sus denuncias se ponen a cero)")
    @PostMapping("/tasks/{id}/restore")
    public void restoreTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        promotionService.restore(CurrentUser.id(jwt), id);
    }

    // ------------------------------------------------------------------ configuración y auditoría

    @Operation(summary = "Configuración de la Arena")
    @GetMapping("/settings")
    public SettingsDto settings() {
        return settingsService.get();
    }

    @Operation(summary = "Cambiar la configuración (se aplica desde la siguiente ronda)")
    @PutMapping("/settings")
    public SettingsDto updateSettings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsDto request) {
        return settingsService.update(CurrentUser.id(jwt), request);
    }

    @Operation(summary = "Registro de acciones de administración (las más recientes primero)")
    @GetMapping("/audit-log")
    public List<AdminDtos.AuditEntry> auditLog(@RequestParam(defaultValue = "50") int limit) {
        return overviewService.auditLog(limit);
    }
}
