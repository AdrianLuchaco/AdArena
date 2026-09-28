package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.security.TokenService;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.dto.LoginRequest;
import com.adarena.user.dto.RegisterRequest;
import com.adarena.user.dto.UserResponse;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.service.WalletService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Casos de uso de autenticación: registrarse, entrar, renovar la sesión y salir.
 */
@Service
public class AuthService {

    /** BCrypt solo usa los primeros 72 BYTES (no caracteres: "ñ" o "€" ocupan más de uno). */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final AppProperties properties;
    private final Clock clock;
    /**
     * Hash de una contraseña inventada. Si el email no existe, comparamos contra él igualmente
     * para que la respuesta tarde lo mismo: así un atacante no averigua qué emails están
     * registrados midiendo tiempos.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService,
                       TokenService tokenService, PasswordEncoder passwordEncoder, WalletService walletService,
                       AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSession register(RegisterRequest request, String userAgent) {
        String email = User.normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG",
                    "The password is too long. Use fewer special characters or accents.");
        }
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().trim(),
                Role.USER, properties.legal().termsVersion(), clock.instant());
        try {
            // saveAndFlush: si dos registros con el mismo email llegan a la vez, el UNIQUE de la
            // BD salta AQUÍ y podemos responder con un error claro.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        // Welcome points: con ellos ya se puede pujar desde el primer día
        long bonus = properties.rewards().signupBonus();
        if (bonus > 0) {
            walletService.grant(user.getId(), bonus, LedgerTransactionType.SIGNUP_BONUS, "signup:" + user.getId(),
                    "USER", user.getId(), "Welcome points");
        }
        return openSession(user, userAgent);
    }

    @Transactional
    public AuthSession login(LoginRequest request, String userAgent) {
        String email = User.normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Your account is disabled.");
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        return openSession(user, userAgent);
    }

    /** Sin @Transactional propio: la transacción (y su bloqueo) vive en RefreshTokenService.rotate. */
    public AuthSession refresh(String refreshTokenValue, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshTokenValue, userAgent);
        return new AuthSession(UserResponse.from(rotation.user()),
                tokenService.issueAccessToken(rotation.user()), rotation.refreshToken());
    }

    public void logout(String refreshTokenValue) {
        refreshTokenService.revokeFamilyOf(refreshTokenValue);
    }

    private AuthSession openSession(User user, String userAgent) {
        IssuedRefreshToken refreshToken = refreshTokenService.issue(user, UUID.randomUUID(), userAgent);
        return new AuthSession(UserResponse.from(user), tokenService.issueAccessToken(user), refreshToken);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Wrong email or password.");
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_TAKEN", "An account with that email already exists.");
    }
}
