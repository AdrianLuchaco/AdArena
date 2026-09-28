package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.user.domain.RefreshToken;
import com.adarena.user.domain.User;
import com.adarena.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens con ROTACIÓN y DETECCIÓN DE REUTILIZACIÓN.
 * <ol>
 *   <li>Al entrar se crea una "familia" y su primer token.</li>
 *   <li>Cada renovación revoca el token usado y emite otro de la misma familia.</li>
 *   <li>Si alguien presenta un token YA revocado, probablemente se lo han robado: se revoca
 *       toda la familia y tanto el ladrón como la víctima tienen que volver a entrar.</li>
 *   <li>Excepción: si se revocó hace menos de {@code reuse-grace} (10 s) suele ser un caso
 *       inocente (dos pestañas renovando a la vez). Se responde 401 REFRESH_TOKEN_RACE sin
 *       revocar nada; el frontend reintenta y el navegador ya envía la cookie nueva.</li>
 * </ol>
 * En la BD solo se guarda el SHA-256 del token. Si alguien robara la base de datos, no podría usarlos.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Emite un token nuevo. {@code familyId} nuevo al entrar; el mismo al rotar. */
    @Transactional
    public IssuedRefreshToken issue(User user, UUID familyId, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.jwt().refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(value), familyId, expiresAt, truncate(userAgent)));
        return new IssuedRefreshToken(value, expiresAt);
    }

    /**
     * Cambia un token válido por otro nuevo. {@code noRollbackFor}: aunque lancemos un error
     * (p. ej. al detectar reutilización), la revocación de la familia SÍ debe guardarse.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String presentedValue, String userAgent) {
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHashForUpdate(hash(presentedValue))
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_TOKEN_INVALID",
                        "Your session is not valid. Please log in again."));

        if (token.getRevokedAt() != null) {
            if (token.getRevokedAt().isAfter(now.minus(properties.refreshCookie().reuseGrace()))) {
                throw ApiException.unauthorized("REFRESH_TOKEN_RACE",
                        "Your session was being renewed in another tab. Try again.");
            }
            int revoked = repository.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} (family {}, {} tokens revoked)",
                    token.getUser().getId(), token.getFamilyId(), revoked);
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED",
                    "For your security we logged you out. Please log in again.");
        }
        if (!token.isActive(now)) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED",
                    "Your session has expired. Please log in again.");
        }
        User user = token.getUser();
        if (!user.isEnabled()) {
            repository.revokeFamily(token.getFamilyId(), now);
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Your account is disabled.");
        }

        token.revoke(now);
        IssuedRefreshToken next = issue(user, token.getFamilyId(), userAgent);
        return new Rotation(user, next);
    }

    /** Cierra la sesión: revoca toda la familia del token presentado (si existe). */
    @Transactional
    public void revokeFamilyOf(String presentedValue) {
        repository.findByTokenHash(hash(presentedValue))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
    }

    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }
}
