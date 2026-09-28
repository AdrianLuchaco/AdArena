package com.adarena.user.controller;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.user.dto.AuthResponse;
import com.adarena.user.dto.ForgotPasswordRequest;
import com.adarena.user.dto.LoginRequest;
import com.adarena.user.dto.RegisterRequest;
import com.adarena.user.dto.ResetPasswordRequest;
import com.adarena.user.service.AuthService;
import com.adarena.user.service.AuthSession;
import com.adarena.user.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Tag(name = "Autenticación", description = "Registro, login, renovación de sesión y logout")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** La cookie solo viaja a /api/auth/*: el resto de la API nunca la recibe. */
    private static final String COOKIE_PATH = "/api/auth";

    /** Errores de refresh tras los que la cookie ya no sirve y conviene borrarla del navegador. */
    private static final Set<String> TERMINAL_REFRESH_ERRORS = Set.of(
            "REFRESH_TOKEN_INVALID", "REFRESH_TOKEN_REUSED", "REFRESH_TOKEN_EXPIRED", "ACCOUNT_DISABLED");

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AppProperties.RefreshCookie cookieProperties;
    private final Clock clock;

    public AuthController(AuthService authService, PasswordResetService passwordResetService, AppProperties properties,
                          Clock clock) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.cookieProperties = properties.refreshCookie();
        this.clock = clock;
    }

    @Operation(summary = "Crear cuenta", description = "Devuelve un access token y deja el refresh token en una cookie HttpOnly.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return respond(HttpStatus.CREATED, authService.register(request, userAgent));
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return respond(HttpStatus.OK, authService.login(request, userAgent));
    }

    @Operation(summary = "Renovar la sesión",
            description = "Usa la cookie del refresh token (la envía el navegador solo). Devuelve un access token nuevo y rota la cookie.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = "${app.refresh-cookie.name}", required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse servletResponse) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("NO_REFRESH_TOKEN", "You are not logged in.");
        }
        try {
            return respond(HttpStatus.OK, authService.refresh(refreshToken, userAgent));
        } catch (ApiException e) {
            if (TERMINAL_REFRESH_ERRORS.contains(e.getCode())) {
                servletResponse.addHeader(HttpHeaders.SET_COOKIE, clearCookie().toString());
            }
            throw e;
        }
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión en el servidor y borra la cookie.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = "${app.refresh-cookie.name}", required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    @Operation(summary = "He olvidado mi contraseña",
            description = "Si existe una cuenta con ese email, le enviamos un enlace. La respuesta es SIEMPRE la misma "
                    + "(202), exista o no: así este formulario no sirve para averiguar quién está registrado.")
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Elegir una contraseña nueva con el enlace del email",
            description = "Cierra todas las sesiones abiertas de la cuenta. Después hay que volver a entrar.")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthSession session) {
        Instant expiresAt = session.accessToken().expiresAt();
        long expiresIn = Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
        AuthResponse body = new AuthResponse(session.accessToken().value(), "Bearer", expiresAt, expiresIn,
                session.user());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(AuthSession session) {
        Duration maxAge = Duration.between(clock.instant(), session.refreshToken().expiresAt());
        return baseCookie(session.refreshToken().value()).maxAge(maxAge).build();
    }

    private ResponseCookie clearCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(COOKIE_PATH);
        if (cookieProperties.hasDomain()) {
            builder.domain(cookieProperties.domain());
        }
        return builder;
    }
}
