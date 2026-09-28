package com.adarena.auction.dto;

import java.time.Instant;

/**
 * Tu situación en la ronda de hoy (tarjeta "Tu puja").
 *
 * @param roundOpen       se puede pujar ahora mismo
 * @param endsAt          fin de la ronda (null si no hay)
 * @param hasAdProfile    ya tienes anuncio (requisito para pujar)
 * @param totalPoints      tu total de hoy (0 si no participas)
 * @param carriedInPoints  parte de tu total que viene arrastrada de ayer
 * @param position        tu posición (null si no participas)
 * @param minNextBidPoints mínimo de tu próxima aportación
 */
public record MyArenaStatus(
        boolean roundOpen,
        Instant endsAt,
        boolean hasAdProfile,
        long totalPoints,
        long carriedInPoints,
        Integer position,
        long minNextBidPoints,
        long availablePoints,
        long reservedPoints
) {
}
