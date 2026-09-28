package com.adarena.auction.event;

import java.util.List;
import java.util.UUID;

/**
 * Algo ha cambiado en la Arena (una puja, una ronda nueva…). Se publica DENTRO de la transacción
 * y se difunde por WebSocket solo DESPUÉS del commit: nunca anunciamos algo que luego se deshace.
 *
 * @param outbidUserIds usuarios que iban por delante del pujador y ahora van por detrás (regla 4)
 * @param extended      true si la puja ha alargado el contador (anti-sniping)
 */
public record ArenaChangedEvent(UUID auctionId, UUID bidderId, List<UUID> outbidUserIds, boolean extended) {

    public static ArenaChangedEvent roundOpened(UUID auctionId) {
        return new ArenaChangedEvent(auctionId, null, List.of(), false);
    }

    /** Ha cambiado la portada (anuncio aprobado, ranking recalculado tras una moderación…). */
    public static ArenaChangedEvent homeChanged() {
        return new ArenaChangedEvent(null, null, List.of(), false);
    }
}
