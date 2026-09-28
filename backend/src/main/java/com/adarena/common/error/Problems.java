package com.adarena.common.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Fábrica de respuestas de error RFC 9457. Todas las respuestas de error de la API tienen esta forma:
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Conflict",
 *   "status": 409,
 *   "detail": "An account with that email already exists.",
 *   "instance": "/api/auth/register",
 *   "code": "EMAIL_TAKEN",
 *   "timestamp": "2026-09-26T10:00:00Z"
 * }
 * </pre>
 */
public final class Problems {

    public static final String CODE = "code";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORS = "errors";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return withDefaults(problem, code);
    }

    /** Añade {@code code} y {@code timestamp} si aún no los tiene. */
    public static ProblemDetail withDefaults(ProblemDetail problem, String code) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            problem.setProperty(CODE, code);
        }
        if (!problem.getProperties().containsKey(TIMESTAMP)) {
            problem.setProperty(TIMESTAMP, Instant.now().toString());
        }
        return problem;
    }
}
