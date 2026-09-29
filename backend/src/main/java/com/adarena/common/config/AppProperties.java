package com.adarena.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Todas las propiedades propias ({@code app.*} en application.yml), con tipos y validadas al
 * arrancar: si falta algo obligatorio, la aplicación no arranca y dice qué falta.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotEmpty List<String> frontendOrigins,
        String publicUrl,
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid RefreshCookie refreshCookie,
        @NotNull @Valid Legal legal,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Jobs jobs,
        @NotNull @Valid Mail mail,
        @NotNull @Valid Rewards rewards,
        @NotNull @Valid Previews previews,
        Admin admin
) {

    /**
     * Dirección pública de la web, para los enlaces de los emails ("vuelve a pujar"). Si no se
     * configura, se usa el primer origen del frontend.
     */
    public String webUrl() {
        String url = publicUrl == null || publicUrl.isBlank() ? frontendOrigins.getFirst() : publicUrl;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {
    }

    public record RefreshCookie(
            @NotBlank String name,
            boolean secure,
            @NotBlank String sameSite,
            String domain,
            @NotNull Duration reuseGrace
    ) {
        public boolean hasDomain() {
            return domain != null && !domain.isBlank();
        }
    }

    public record Legal(@NotBlank String termsVersion) {
    }

    /**
     * @param proxySecret clave compartida con la web (PROXY_SECRET). En la nube, las llamadas a la API
     *                    llegan a través de la web (Vercel), así que el backend ve la IP de Vercel. La
     *                    web añade la IP real del visitante y esta clave; solo si la clave coincide se
     *                    cree esa IP. Vacía = se usa la IP de la conexión (en local, o sin proxy).
     */
    public record RateLimit(boolean enabled, @NotNull List<@Valid Rule> rules, String proxySecret) {

        /**
         * Límite de {@code capacity} peticiones por {@code period} y por IP, para las rutas que
         * empiezan por {@code pathPrefix} salvo las de {@code excludePathPrefixes}.
         */
        public record Rule(
                @NotBlank String name,
                String method,
                @NotBlank String pathPrefix,
                List<String> excludePathPrefixes,
                @Positive int capacity,
                @NotNull Duration period
        ) {
            public boolean matches(String requestMethod, String path) {
                boolean methodMatches = method == null || method.isBlank()
                        || HttpMethod.valueOf(method).matches(requestMethod);
                boolean excluded = excludePathPrefixes != null
                        && excludePathPrefixes.stream().anyMatch(path::startsWith);
                return methodMatches && path.startsWith(pathPrefix) && !excluded;
            }
        }
    }

    /**
     * Tareas automáticas: cierre diario, apertura de rondas, caducidades y envío de emails.
     * Los tests las desactivan para decidir ellos cuándo se ejecuta cada cosa.
     */
    public record Jobs(boolean enabled) {
    }

    /**
     * Emails. Dos formas de enviarlos:
     * <ul>
     *   <li>{@code brevoApiKey}: por la API HTTPS de Brevo (gratis, 300 al día, sin dominio propio).
     *       Recomendado en la nube: muchos alojamientos gratuitos bloquean los puertos SMTP.</li>
     *   <li>{@code host}: por SMTP (Resend, el correo de tu dominio…).</li>
     * </ul>
     * Sin ninguna de las dos, los emails no se envían: solo se escriben en el log (cómodo en local).
     */
    public record Mail(
            @NotBlank String from,
            String host,
            @NotNull Integer port,
            String username,
            String password,
            boolean ssl,
            String brevoApiKey
    ) {
        public boolean usesBrevo() {
            return brevoApiKey != null && !brevoApiKey.isBlank();
        }

        public boolean isConfigured() {
            return usesBrevo() || (host != null && !host.isBlank());
        }
    }

    /**
     * Cómo se ganan los Crown Points. Los valores por defecto están en application.yml.
     *
     * @param signupBonus puntos de bienvenida al crear la cuenta (una sola vez)
     * @param winnerBonus puntos de regalo al ganador cuando su anuncio sale en portada, para que
     *                    pueda volver a pujar
     */
    public record Rewards(
            @PositiveOrZero long signupBonus,
            @PositiveOrZero long winnerBonus,
            @NotNull @Valid Views views,
            @NotNull @Valid Tasks tasks
    ) {
    }

    /**
     * Lectura de la web de cada proyecto (título, logo, imágenes, frases destacadas y si se puede
     * mostrar dentro de LaunchCrown).
     *
     * @param enabled         false en los tests: nunca se sale a internet
     * @param maxAge          a partir de cuándo se vuelve a leer una web
     * @param refreshCooldown tiempo mínimo entre dos lecturas de la misma web pedidas por su dueño
     * @param userAgent       cómo se presenta LaunchCrown ante las webs que lee
     */
    public record Previews(
            boolean enabled,
            @NotNull Duration maxAge,
            @NotNull Duration refreshCooldown,
            @NotBlank String userAgent
    ) {
    }

    /**
     * Intercambio de visitas: {@code tickPoints} puntos por cada {@code tickSeconds} segundos viendo un
     * proyecto; al llegar a {@code bonusAfterSeconds} segundos, {@code bonusPoints} más. Como mucho
     * {@code dailyCapPerProject} puntos al día por proyecto.
     */
    public record Views(
            @Positive int tickSeconds,
            @Positive int tickPoints,
            @Positive int bonusAfterSeconds,
            @PositiveOrZero int bonusPoints,
            @Positive int dailyCapPerProject
    ) {
        /** Ticks necesarios para el bonus (60 s / 10 s = 6). */
        public int ticksForBonus() {
            return Math.max(1, bonusAfterSeconds / tickSeconds);
        }
    }

    /**
     * Tareas sociales (Créditos extra).
     *
     * @param minSeconds       segundos que tienen que pasar entre abrir el enlace y reclamar los puntos
     * @param maxPerDay        tareas con recompensa por usuario y día
     * @param maxActivePerUser promociones activas que puede tener cada usuario
     * @param reportsToHide    denuncias de usuarios distintos que ocultan una tarea automáticamente
     */
    public record Tasks(
            @Positive int rewardPoints,
            @Positive int featuredRewardPoints,
            @Positive int minSeconds,
            @Positive int maxPerDay,
            @Positive int maxActivePerUser,
            @Positive int reportsToHide
    ) {
    }

    public record Admin(String email, String password, String displayName) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
