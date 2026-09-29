package com.adarena.common.jobs;

import com.adarena.adslot.service.ModerationService;
import com.adarena.auction.service.ArenaCloseService;
import com.adarena.auction.service.ArenaLifecycleService;
import com.adarena.notification.service.OutboxEmailSender;
import com.adarena.site.service.SitePreviewWarmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Todas las tareas automáticas de LaunchCrown. Se desactivan con app.jobs.enabled=false (los tests lo
 * hacen para decidir ellos cuándo pasa cada cosa).
 * <p>
 * Todas son seguras aunque se ejecuten dos veces a la vez (por ejemplo, con dos instancias del
 * backend durante un despliegue): cada una bloquea las filas que toca y comprueba el estado antes
 * de actuar. Un fallo en una tarea se registra y se reintenta en la siguiente vuelta.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.jobs.enabled", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final ArenaCloseService closeService;
    private final ArenaLifecycleService lifecycleService;
    private final ModerationService moderationService;
    private final OutboxEmailSender emailSender;
    private final SitePreviewWarmer sitePreviewWarmer;

    public ScheduledJobs(ArenaCloseService closeService, ArenaLifecycleService lifecycleService,
                         ModerationService moderationService, OutboxEmailSender emailSender,
                         SitePreviewWarmer sitePreviewWarmer) {
        this.closeService = closeService;
        this.lifecycleService = lifecycleService;
        this.moderationService = moderationService;
        this.emailSender = emailSender;
        this.sitePreviewWarmer = sitePreviewWarmer;
    }

    /**
     * Cada 5 segundos: ¿ha llegado la hora de cerrar la ronda? Si sí, se cierra y se abre la
     * siguiente. Si no hay ninguna abierta (primer arranque), se abre.
     * La primera vez espera 10 s: en local, los datos de ejemplo se crean justo al arrancar y solo
     * si no hay ninguna ronda (si la tarea abriera una antes, no se crearían).
     */
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT5S")
    public void arena() {
        try {
            closeService.closeDueRound();
        } catch (RuntimeException e) {
            log.error("Daily close failed; it will be retried in a few seconds", e);
        }
        try {
            lifecycleService.ensureOpenRound()
                    .ifPresent(round -> log.info("Round {} opened, closes at {}", round.getAuctionDate(), round.getEndsAt()));
        } catch (DataIntegrityViolationException e) {
            log.info("Another instance opened the round first");
        } catch (RuntimeException e) {
            log.error("Could not open a round", e);
        }
    }

    /** Cada minuto: anuncios sin moderar cuya ventana terminó (se devuelven el 100 % de los puntos). */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT1M")
    public void expirations() {
        try {
            int slots = moderationService.expireOverdueSlots();
            if (slots > 0) {
                log.warn("{} winning ad(s) expired without moderation and were refunded", slots);
            }
        } catch (RuntimeException e) {
            log.error("Ad slot expiration failed", e);
        }
    }

    /**
     * Cada 15 minutos: tener al día las webs de los proyectos de hoy, las promociones y los ganadores
     * pendientes (las lecturas van en segundo plano; solo las que tienen más de 20 h).
     */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT15M")
    public void sitePreviews() {
        try {
            sitePreviewWarmer.warm();
        } catch (RuntimeException e) {
            log.error("Could not refresh site previews", e);
        }
    }

    /** Cada 10 segundos: enviar los emails pendientes. */
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT10S")
    public void emails() {
        try {
            emailSender.sendDueEmails();
        } catch (RuntimeException e) {
            log.error("Email sending failed", e);
        }
    }
}
