package com.adarena.user.service;

import java.time.Instant;

/** Refresh token recién emitido. {@code value} es el texto en claro: solo existe en memoria y en la cookie. */
public record IssuedRefreshToken(String value, Instant expiresAt) {
}
