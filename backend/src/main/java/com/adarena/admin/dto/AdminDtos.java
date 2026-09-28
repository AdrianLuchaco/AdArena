package com.adarena.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Peticiones y respuestas del panel de administración. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * Resumen de la plataforma.
     *
     * @param spentPoints          puntos gastados en la Arena desde el principio (ganadores + 50 % perdido)
     * @param issuedPoints         puntos repartidos desde el principio (bienvenida, visitas, tareas)
     * @param issuedTodayPoints    puntos repartidos hoy
     * @param usersAvailablePoints puntos libres de todos los usuarios
     * @param usersReservedPoints  puntos en pujas o retenidos de ganadores pendientes
     * @param ledgerMismatches     debe ser SIEMPRE 0: si no, las cuentas no cuadran
     * @param activeTasks          promociones publicadas en Créditos extra
     * @param reportedTasks        promociones visibles con alguna denuncia (revísalas)
     * @param hiddenTasks          promociones ocultas
     * @param emailsDelivered      true si hay SMTP configurado (si no, los emails solo van al log)
     */
    public record Overview(
            long spentPoints,
            long issuedPoints,
            long issuedTodayPoints,
            long usersAvailablePoints,
            long usersReservedPoints,
            long ledgerMismatches,
            long users,
            long pendingAdSlots,
            long activeTasks,
            long reportedTasks,
            long hiddenTasks,
            long failedEmails,
            boolean emailsDelivered,
            OpenRound openRound
    ) {
    }

    public record OpenRound(UUID id, LocalDate roundDate, Instant endsAt, int participants, long totalPoints) {
    }

    public record RejectRequest(
            @Schema(description = "Motivo (se envía al usuario)", example = "La imagen no se ve bien.")
            @NotBlank(message = "Enter a reason.")
            @Size(max = 500, message = "500 characters at most.")
            String reason
    ) {
    }

    public record AuditEntry(UUID id, String adminName, String action, String targetType, String targetId,
                             String details, Instant createdAt) {
    }
}
