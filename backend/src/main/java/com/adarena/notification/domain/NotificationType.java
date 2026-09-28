package com.adarena.notification.domain;

public enum NotificationType {
    /** Otra persona te ha superado (regla 4). */
    OUTBID,
    /** Has ganado la ronda; tu anuncio pasa a moderación. */
    AUCTION_WON,
    /** No has ganado; conservas el arrastre para la ronda siguiente. */
    AUCTION_LOST,
    /** Tu anuncio ya está en portada. */
    AD_APPROVED,
    /** Tu anuncio se ha rechazado y se te han devuelto tus puntos. */
    AD_REJECTED,
    /** Tu anuncio no se moderó a tiempo y se te han devuelto tus puntos. */
    WINNER_REFUNDED,
    /** El ganador fue rechazado y ahora el candidato a la portada eres tú. */
    CANDIDATE_PROMOTED,
    /** Una de tus promociones se ha ocultado (por el admin o por denuncias). */
    TASK_HIDDEN
}
