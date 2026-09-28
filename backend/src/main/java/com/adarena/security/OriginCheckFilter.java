package com.adarena.security;

import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.common.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Protección extra contra CSRF (una web maliciosa que hace que TU navegador envíe una petición a
 * AdArena) en las rutas de /api/auth, que son las únicas que usan la cookie de sesión:
 * <ul>
 *   <li>Si la petición trae la cabecera {@code Origin} (los navegadores la ponen siempre en los
 *       POST), debe ser uno de los orígenes del frontend. Si no, 403.</li>
 *   <li>Sin {@code Origin} (herramientas como curl, o apps): se deja pasar; no es un navegador,
 *       así que no puede llevar la cookie de una víctima.</li>
 * </ul>
 * Es una segunda capa: la cookie ya es SameSite=Lax (el navegador no la envía en POST desde otras
 * webs). El resto de la API usa la cabecera Authorization, que otra web no puede añadir.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PREFIX = "/api/auth/";

    private final Set<String> allowedOrigins;
    private final ProblemResponseWriter writer;

    public OriginCheckFilter(List<String> allowedOrigins, ProblemResponseWriter writer) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            writer.write(request, response, Problems.of(HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED",
                    "Request rejected: it does not come from the AdArena website."));
            return;
        }
        chain.doFilter(request, response);
    }
}
