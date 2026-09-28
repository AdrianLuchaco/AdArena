package com.adarena.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.common.error.Problems;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limita peticiones por IP con el algoritmo "token bucket" (Bucket4j): cada IP tiene un cubo
 * con N fichas por regla; cada petición gasta una y el cubo se rellena poco a poco. Sin fichas
 * → 429 Too Many Requests con la cabecera Retry-After.
 * <p>
 * Los cubos viven en memoria (Caffeine, con caducidad para no crecer sin límite). Con una sola
 * instancia del backend es suficiente; con varias, cada una contaría por separado.
 * <p>
 * No es un @Component a propósito: se inserta dentro de la cadena de Spring Security, justo
 * después del filtro CORS, para que las respuestas 429 lleven cabeceras CORS y el frontend
 * pueda leerlas.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties.RateLimit config;
    private final ProblemResponseWriter writer;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(200_000)
            .build();

    public RateLimitFilter(AppProperties.RateLimit config, ProblemResponseWriter writer) {
        this.config = config;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = clientIp(request);
        String path = request.getRequestURI();

        for (AppProperties.RateLimit.Rule rule : config.rules()) {
            if (!rule.matches(request.getMethod(), path)) {
                continue;
            }
            Bucket bucket = buckets.get(rule.name() + '|' + clientIp, key -> newBucket(rule));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long waitSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds));
                writer.write(request, response, Problems.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many requests. Wait " + waitSeconds + " s and try again."));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Cabeceras que añade la web (proxy.ts) al reenviar las llamadas a la API. */
    static final String CLIENT_IP_HEADER = "X-AdArena-Client-IP";
    static final String PROXY_SECRET_HEADER = "X-AdArena-Proxy-Secret";

    /**
     * La IP del visitante. Si la llamada llega a través de la web (con la clave PROXY_SECRET
     * correcta), la IP real que ha puesto la web; si no, la de la conexión. Sin la clave, nadie puede
     * inventarse una IP para saltarse los límites.
     */
    String clientIp(HttpServletRequest request) {
        String secret = config.proxySecret();
        String forwarded = request.getHeader(CLIENT_IP_HEADER);
        String presented = request.getHeader(PROXY_SECRET_HEADER);
        if (secret != null && secret.length() >= 16 && forwarded != null && presented != null
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))
                && looksLikeIp(forwarded)) {
            return forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean looksLikeIp(String value) {
        String ip = value.trim();
        return !ip.isEmpty() && ip.length() <= 45 && ip.chars().allMatch(c ->
                Character.digit(c, 16) >= 0 || c == '.' || c == ':');
    }

    private static Bucket newBucket(AppProperties.RateLimit.Rule rule) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.capacity(), rule.period())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
