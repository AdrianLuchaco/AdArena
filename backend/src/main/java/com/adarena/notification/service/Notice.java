package com.adarena.notification.service;

import com.adarena.notification.domain.NotificationType;

/**
 * Un aviso para un usuario: lo que ve en la campana de su panel y, si {@code email} es true,
 * también el email que recibe.
 *
 * @param link        ruta de la web a la que lleva el aviso ("/race", "/account/points"…)
 * @param email       enviar también un email
 * @param emailButton texto del botón del email ("Volver a pujar")
 */
public record Notice(NotificationType type, String title, String body, String link, boolean email,
                     String emailButton) {
}
