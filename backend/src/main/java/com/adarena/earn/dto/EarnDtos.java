package com.adarena.earn.dto;

import com.adarena.earn.domain.SocialPlatform;
import com.adarena.earn.domain.SocialTaskStatus;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrada y salida de "Gana puntos", "Créditos extra" y "Promocionar". */
public final class EarnDtos {

    private EarnDtos() {
    }

    // ------------------------------------------------------------------ reglas

    /** Las reglas vigentes, para que la web las explique con los números reales. */
    public record Rules(int tickSeconds, int tickPoints, int bonusAfterSeconds, int bonusPoints,
                        int dailyCapPerProject, int taskRewardPoints, int taskMinSeconds, int tasksPerDay,
                        long signupBonus, long winnerBonus) {
    }

    // ------------------------------------------------------------------ ver proyectos

    /**
     * Un proyecto de la Arena de hoy visto desde "Gana puntos".
     *
     * @param own               es tu propio proyecto (no da puntos)
     * @param pointsEarnedToday lo que ya has ganado hoy viéndolo (máximo {@code dailyCap})
     * @param site              su web: si se ve dentro de LaunchCrown, su logo, su foto…
     */
    public record EarnProject(UUID id, int position, String companyName, String description, String imageUrl,
                              long totalPoints, boolean own, int pointsEarnedToday, int dailyCap, SiteInfo site) {
    }

    /**
     * Resumen de "Gana puntos".
     *
     * @param earnedTodayFromViews puntos ganados hoy viendo proyectos
     * @param earnedTodayFromTasks puntos ganados hoy con Créditos extra
     */
    public record EarnOverview(long availablePoints, long reservedPoints, long earnedTodayFromViews,
                               long earnedTodayFromTasks, long tasksDoneToday, Rules rules,
                               List<EarnProject> projects) {
    }

    /**
     * Estado de la visita a un proyecto (al abrir su página).
     *
     * @param canEarn si sigue ganando puntos al verlo
     * @param reason  por qué no: OWN_PROJECT, CAP_REACHED o NOT_IN_ARENA
     */
    public record ViewStatus(UUID projectId, boolean canEarn, String reason, int pointsEarnedToday, int dailyCap,
                             int ticks, boolean bonusAwarded, Rules rules) {
    }

    /**
     * Petición de puntos por tiempo de visita.
     *
     * @param ticks cuántos tramos de 10 s pide (varios si la web estuvo un rato en otra ventana). El
     *              servidor nunca da más de los que caben en el tiempo real transcurrido.
     */
    public record TickRequest(
            @Schema(example = "1")
            @Min(value = 1, message = "At least 1.")
            @Max(value = 6, message = "6 at most.")
            Integer ticks
    ) {
        public int ticksOrOne() {
            return ticks == null ? 1 : ticks;
        }
    }

    /**
     * Resultado de un tick.
     *
     * @param pointsAwarded puntos de esta petición (10 por tramo, más el bonus al llegar a 60 s)
     * @param ticksAwarded  tramos de 10 s que se han contado
     * @param bonusAwarded  incluía el bonus de los 60 s
     * @param capReached    ya no se ganan más puntos hoy con este proyecto
     */
    public record TickResult(int pointsAwarded, int ticksAwarded, boolean bonusAwarded, int pointsEarnedToday,
                             int dailyCap, boolean capReached, long availablePoints) {
    }

    /** Un proyecto en su página propia (público). */
    public record PublicProject(UUID id, String companyName, String description, String websiteUrl, String imageUrl,
                                long totalPoints, long carriedInPoints, Integer position, boolean inArena,
                                Instant roundEndsAt, SiteInfo site) {
    }

    // ------------------------------------------------------------------ Créditos extra

    /**
     * Una tarea de Créditos extra.
     *
     * @param state AVAILABLE (sin empezar), STARTED (enlace abierto, falta reclamar) o DONE (hecha hoy)
     */
    public record TaskItem(UUID id, SocialPlatform platform, String platformLabel, String title, String description,
                           String url, int rewardPoints, boolean featured, String state, Instant startedAt,
                           SiteInfo site) {
    }

    public record TasksOverview(long tasksDoneToday, int tasksPerDay, long earnedToday, Rules rules,
                                List<TaskItem> tasks) {
    }

    /** Al abrir el enlace: vuelve aquí pasados {@code minSeconds} segundos para reclamar. */
    public record TaskStarted(UUID id, String url, int minSeconds, Instant startedAt) {
    }

    public record TaskClaimed(UUID id, int pointsAwarded, long tasksDoneToday, int tasksPerDay, long availablePoints) {
    }

    public record ReportRequest(
            @Schema(example = "El enlace lleva a una web de estafas")
            @NotBlank(message = "Tell us what's wrong with this link.")
            @Size(max = 300, message = "300 characters at most.")
            String reason
    ) {
    }

    // ------------------------------------------------------------------ Promocionar

    public record PromotionRequest(
            @Schema(example = "Mi canal de recetas fáciles")
            @NotBlank(message = "Enter a title.")
            @Size(max = 80, message = "80 characters at most.")
            String title,

            @Schema(example = "Recetas de 15 minutos cada semana.")
            @Size(max = 200, message = "200 characters at most.")
            String description,

            @Schema(example = "https://www.youtube.com/@micanal")
            @NotBlank(message = "Paste the link you want to promote.")
            @Size(max = 2048, message = "The link is too long.")
            String url
    ) {
    }

    /**
     * Una de tus promociones.
     *
     * @param visitsToday visitas con recompensa de hoy
     */
    public record Promotion(UUID id, SocialPlatform platform, String platformLabel, String title, String description,
                            String url, SocialTaskStatus status, String hiddenReason, int rewardPoints,
                            int totalVisits, long visitsToday, Instant createdAt, SiteInfo site) {
    }

    public record PromotionsOverview(int maxActive, int rewardPoints, List<Promotion> promotions) {
    }

    /** Una promoción vista desde el panel de administración. */
    public record AdminTask(UUID id, SocialPlatform platform, String title, String description, String url,
                            SocialTaskStatus status, String hiddenReason, int reports, int completions,
                            boolean featured, int rewardPoints, String ownerEmail, String ownerName, Instant createdAt, List<String> reportReasons) {
    }
}
