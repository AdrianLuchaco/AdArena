package com.adarena.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que SOLO existe en los tests (está en src/test) para comprobar que /api/admin/**
 * exige el rol ADMIN antes de que existan los endpoints reales de administración (fase 8).
 */
@RestController
class TestAdminController {

    @GetMapping("/api/admin/_test/ping")
    String ping() {
        return "pong";
    }
}
