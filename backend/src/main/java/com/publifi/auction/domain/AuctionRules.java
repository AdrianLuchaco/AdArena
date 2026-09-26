package com.publifi.auction.domain;

import jakarta.persistence.Embeddable;

/**
 * Reglas congeladas al abrir una subasta (copia de {@code app_settings} en ese momento).
 * Así, si el admin cambia la configuración, la subasta en curso no cambia de reglas.
 */
@Embeddable
public record AuctionRules(
        long minBidCents,
        long minIncrementCents,
        int carryOverPercent,
        int antiSnipingWindowSeconds,
        int antiSnipingExtensionSeconds,
        int antiSnipingMaxExtensions
) {

    /**
     * Parte del total de un perdedor que pasa a la subasta siguiente. Se redondea hacia abajo
     * (al céntimo) y el resto es ingreso: 1,01 € al 50 % arrastra 0,50 € y pierde 0,51 €.
     */
    public long carryOverOf(long totalCents) {
        return totalCents * carryOverPercent / 100;
    }
}
