package com.adarena.auction.domain;

import jakarta.persistence.Embeddable;

/**
 * Reglas congeladas al abrir una subasta (copia de {@code app_settings} en ese momento).
 * Así, si el admin cambia la configuración, la subasta en curso no cambia de reglas.
 */
@Embeddable
public record AuctionRules(
        long minBidPoints,
        long minIncrementPoints,
        int carryOverPercent,
        int antiSnipingWindowSeconds,
        int antiSnipingExtensionSeconds,
        int antiSnipingMaxExtensions
) {

    /**
     * Parte del total de un perdedor que pasa a la subasta siguiente. Se redondea hacia abajo
     * (al punto) y el resto se gasta: 101 puntos al 50 % arrastran 50 y pierden 51.
     */
    public long carryOverOf(long totalPoints) {
        return totalPoints * carryOverPercent / 100;
    }
}
