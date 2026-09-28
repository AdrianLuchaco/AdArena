package com.adarena.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Utilidad para leer el usuario autenticado en un controlador:
 * <pre>
 *   public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
 *       UUID userId = CurrentUser.id(jwt);
 * </pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
