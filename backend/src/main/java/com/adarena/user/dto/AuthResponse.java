package com.adarena.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Respuesta de registro, login y refresh. El refresh token NO va aquí: viaja en una cookie
 * HttpOnly que JavaScript no puede leer.
 */
public record AuthResponse(
        @Schema(description = "JWT para la cabecera Authorization: Bearer <token>")
        String accessToken,
        @Schema(example = "Bearer")
        String tokenType,
        Instant expiresAt,
        @Schema(description = "Segundos hasta que caduca el access token", example = "900")
        long expiresIn,
        UserResponse user
) {
}
