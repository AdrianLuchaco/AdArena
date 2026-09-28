package com.adarena.home.dto;

import com.adarena.site.dto.SiteDtos.Showcase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Todo lo que necesita la portada en una sola respuesta. Es también el mensaje que se envía por
 * WebSocket (/topic/arena) cada vez que algo cambia.
 *
 * @param serverTime hora del servidor, para que el navegador corrija el desfase de su reloj
 * @param state      qué mostrar en la portada
 * @param currentAd  anuncio vigente (solo si state = AD)
 * @param round      ronda abierta con sus proyectos (null si todavía no hay ninguna)
 */
public record HomeResponse(
        Instant serverTime,
        HomeState state,
        CurrentAd currentAd,
        RoundSummary round
) {

    @Schema(description = """
            AD: hay anuncio ganador aprobado.
            PENDING_REVIEW: hay ganador, pero el anuncio está en revisión.
            NO_BIDS: ayer nadie participó ("Hoy nadie ha pujado").
            NO_AD: no hay anuncio (se muestra la lista de proyectos).""")
    public enum HomeState {
        AD, PENDING_REVIEW, NO_BIDS, NO_AD
    }

    /**
     * El anuncio ganador que está en portada.
     *
     * @param wonWithPoints lo que pujó en total para ganar
     * @param roundDate    día en que compitió (y ganó)
     * @param showcase     su presentación animada (leída de su web y aprobada); null si no la hay
     */
    public record CurrentAd(
            String companyName,
            String description,
            String websiteUrl,
            String imageUrl,
            Instant startsAt,
            Instant endsAt,
            long wonWithPoints,
            LocalDate roundDate,
            Showcase showcase
    ) {
    }

    public record RoundSummary(
            UUID id,
            LocalDate roundDate,
            Instant endsAt,
            int participants,
            List<ProjectEntry> ranking
    ) {
    }

    /** Un proyecto que compite hoy, con su posición y su total. */
    public record ProjectEntry(
            UUID id,
            int position,
            String companyName,
            String description,
            String websiteUrl,
            String imageUrl,
            long totalPoints,
            long carriedInPoints
    ) {
    }
}
