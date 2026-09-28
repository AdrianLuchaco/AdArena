package com.adarena.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Página del historial de rondas anteriores (más recientes primero). */
public record HistoryPage(List<PastRound> items, int page, int totalPages, long totalItems) {

    @Schema(description = """
            WINNER: hubo ganador y su anuncio se publicó.
            PENDING_REVIEW: hubo ganador y su anuncio está en revisión.
            NO_BIDS: nadie participó ese día.
            NO_WINNER: hubo participantes, pero ningún anuncio llegó a publicarse.""")
    public enum Outcome {
        WINNER, PENDING_REVIEW, NO_BIDS, NO_WINNER
    }

    /**
     * Una ronda cerrada.
     *
     * @param roundDate    día en que se compitió
     * @param showcaseDate día en que el ganador ocupó la portada (normalmente el siguiente)
     */
    public record PastRound(
            LocalDate roundDate,
            LocalDate showcaseDate,
            Instant closedAt,
            Outcome outcome,
            PastProject winner,
            List<PastProject> projects
    ) {
    }

    /** Un proyecto tal y como estaba al cerrar la ronda (su anuncio "congelado"). */
    public record PastProject(
            UUID id,
            int rank,
            String companyName,
            String description,
            String websiteUrl,
            String imageUrl,
            long totalPoints
    ) {
    }
}
