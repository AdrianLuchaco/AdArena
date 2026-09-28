package com.adarena.auction.dto;

import java.time.Instant;

/**
 * Resultado de una puja.
 *
 * @param totalPoints     tu total acumulado hoy
 * @param position       tu posición en la clasificación (1 = vas primero)
 * @param endsAt         fin de la ronda (puede haberse alargado)
 * @param extended       true si tu puja ha alargado el contador
 * @param replayed       true si era una repetición (misma Idempotency-Key) y no se ha cobrado de nuevo
 */
public record BidResponse(
        long totalPoints,
        int position,
        Instant endsAt,
        boolean extended,
        long availablePoints,
        long reservedPoints,
        boolean replayed
) {
}
