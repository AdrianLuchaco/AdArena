package com.adarena.security;

import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.common.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Respuesta 401 cuando falta el token o no es válido. Distingue dos casos con {@code code}:
 * <ul>
 *   <li>{@code UNAUTHORIZED}: no has enviado token → el frontend manda a "Entrar".</li>
 *   <li>{@code TOKEN_INVALID}: token caducado o manipulado → el frontend intenta renovarlo
 *       con /api/auth/refresh y repite la petición.</li>
 * </ul>
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter writer;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean invalidToken = exception instanceof InvalidBearerTokenException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(request, response, invalidToken
                ? Problems.of(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
                        "Your session has expired or is not valid.")
                : Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "You need to log in."));
    }
}
