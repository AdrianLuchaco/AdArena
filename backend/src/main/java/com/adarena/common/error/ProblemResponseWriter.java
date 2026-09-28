package com.adarena.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

/**
 * Escribe un {@link ProblemDetail} directamente en la respuesta. Lo usan los componentes que se
 * ejecutan ANTES de llegar a los controladores (filtros de seguridad y rate limiting), donde el
 * {@code GlobalExceptionHandler} todavía no actúa.
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    /**
     * El mixin "aplana" las propiedades extra (code, timestamp...) al nivel raíz del JSON,
     * igual que hacen los controladores.
     */
    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper.rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
