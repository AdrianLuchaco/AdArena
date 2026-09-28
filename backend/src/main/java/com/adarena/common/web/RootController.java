package com.adarena.common.web;

import com.adarena.common.config.AppProperties;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Si alguien abre la dirección del backend en el navegador (http://localhost:8081 o
 * https://api.adarena.com), lo mandamos a la web de verdad en lugar de mostrarle un error.
 */
@Hidden
@RestController
public class RootController {

    private final String frontendUrl;

    public RootController(AppProperties properties) {
        this.frontendUrl = properties.frontendOrigins().getFirst();
    }

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, frontendUrl).build();
    }
}
