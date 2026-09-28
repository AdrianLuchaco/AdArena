package com.adarena.security;

import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.common.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Respuesta 403: has iniciado sesión, pero tu rol no permite esta acción (p. ej. /api/admin). */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writer.write(request, response, Problems.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You don't have permission to do this."));
    }
}
