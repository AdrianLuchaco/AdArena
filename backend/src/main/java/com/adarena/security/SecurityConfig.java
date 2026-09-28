package com.adarena.security;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.security.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.List;

/**
 * Reglas de seguridad de la API.
 * <ul>
 *   <li>Sin sesiones de servidor (STATELESS): cada petición trae su JWT.</li>
 *   <li>CSRF desactivado: las rutas protegidas usan la cabecera Authorization, que un sitio
 *       malicioso no puede añadir. Las rutas que usan cookie (/api/auth/**) están cubiertas por
 *       SameSite=Lax y por {@link OriginCheckFilter} (rechaza orígenes que no son el frontend).</li>
 *   <li>Rutas públicas: "/" (redirige a la web), auth, /api/public/**, salud y Swagger. /api/admin/** exige rol ADMIN.
 *       Todo lo demás exige sesión.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties,
                                            ProblemResponseWriter problemWriter) throws Exception {
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(problemWriter);
        ProblemAccessDeniedHandler accessDeniedHandler = new ProblemAccessDeniedHandler(problemWriter);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // WebSocket: el apretón de manos es público; el token va dentro de STOMP (CONNECT)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        // La API solo devuelve JSON e imágenes: no puede cargar nada ni meterse en un iframe.
                        // (Swagger, que sí necesita scripts, solo existe en local y queda fuera de /api/.)
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.pathPattern("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))
                // Orden: CORS → límite de peticiones por IP → tamaño máximo → origen de las rutas con cookie
                .addFilterAfter(new RateLimitFilter(properties.rateLimit(), problemWriter), CorsFilter.class)
                .addFilterAfter(new RequestSizeLimitFilter(problemWriter), RateLimitFilter.class)
                .addFilterAfter(new OriginCheckFilter(properties.frontendOrigins(), problemWriter), RequestSizeLimitFilter.class);

        return http.build();
    }

    /**
     * Ignora la cabecera Authorization en /api/auth/**: si el frontend enviara un access token
     * caducado al hacer login o refresh, no queremos rechazarlo con 401 por ese token.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/auth/") ? null : defaultResolver.resolve(request);
    }

    /** Convierte el claim "roles": ["ADMIN"] del JWT en la autoridad ROLE_ADMIN de Spring. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** CORS: solo el frontend puede llamar a la API desde el navegador, con cookies incluidas. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.frontendOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key"));
        cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.LOCATION));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** BCrypt con prefijo {bcrypt}: permite cambiar de algoritmo en el futuro sin romper hashes antiguos. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
