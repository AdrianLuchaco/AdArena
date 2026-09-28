package com.adarena.user.service;

import com.adarena.security.AccessToken;
import com.adarena.user.dto.UserResponse;

/** Resultado de registrarse, entrar o renovar: datos del usuario + los dos tokens. */
public record AuthSession(UserResponse user, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
