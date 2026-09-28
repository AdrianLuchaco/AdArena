package com.adarena.realtime;

/**
 * Aviso privado que llega al instante por WebSocket ("te han superado", "has ganado"…).
 *
 * @param type    tipo de aviso (mismos valores que NotificationType)
 * @param link    ruta de la web a la que lleva el aviso
 */
public record ArenaNotification(String type, String title, String message, String link) {

    public static ArenaNotification outbid() {
        return new ArenaNotification("OUTBID", "You've been outbid",
                "Someone just outbid you. Bid again to take the lead.", "/arena");
    }
}
