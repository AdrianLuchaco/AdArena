package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.notification.service.EmailTemplates;
import com.adarena.notification.service.NotificationService;
import com.adarena.user.domain.PasswordResetToken;
import com.adarena.user.domain.User;
import com.adarena.user.repository.PasswordResetTokenRepository;
import com.adarena.user.repository.RefreshTokenRepository;
import com.adarena.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * "He olvidado mi contraseña":
 * <ol>
 *   <li>El usuario escribe su email. Respondemos SIEMPRE lo mismo, exista o no la cuenta: así nadie
 *       puede usar este formulario para averiguar quién está registrado.</li>
 *   <li>Si la cuenta existe, le enviamos un enlace con un token aleatorio (válido 60 minutos, un
 *       solo uso). En la base de datos solo guardamos su hash.</li>
 *   <li>Con el enlace elige una contraseña nueva. Se cierran todas sus sesiones abiertas (por si
 *       alguien más la estaba usando).</li>
 * </ol>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration VALIDITY = Duration.ofMinutes(60);
    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final int TOKEN_BYTES = 32;
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                RefreshTokenRepository refreshTokenRepository, NotificationService notificationService,
                                PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Paso 1. No revela si la cuenta existe: el controlador responde siempre 202. */
    @Transactional
    public void requestReset(String rawEmail) {
        Instant now = clock.instant();
        User user = userRepository.findByEmail(User.normalizeEmail(rawEmail)).orElse(null);
        if (user == null || !user.isEnabled()) {
            return;
        }
        if (tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)))
                >= MAX_REQUESTS_PER_HOUR) {
            log.info("Password reset for user {} ignored: too many requests in the last hour", user.getId());
            return;
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new PasswordResetToken(user.getId(), RefreshTokenService.hash(token), now.plus(VALIDITY)));

        String link = properties.webUrl() + "/reset-password?token=" + token;
        notificationService.sendEmail(user.getEmail(),
                EmailTemplates.passwordReset(user.getDisplayName(), link, VALIDITY.toMinutes()));
    }

    /** Paso 2: el enlace del email + la contraseña nueva. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG",
                    "The password is too long. Use fewer special characters or accents.");
        }
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash(token))
                .filter(found -> found.isUsableAt(now))
                .orElseThrow(() -> ApiException.badRequest("RESET_TOKEN_INVALID",
                        "This link is not valid or has expired. Ask for a new one."));
        User user = userRepository.findById(resetToken.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("RESET_TOKEN_INVALID",
                        "This link is not valid or has expired. Ask for a new one."));

        user.changePassword(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        tokenRepository.invalidateAll(user.getId(), now);
        int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), now);
        log.info("Password reset for user {} ({} sessions closed)", user.getId(), revoked);
    }
}
