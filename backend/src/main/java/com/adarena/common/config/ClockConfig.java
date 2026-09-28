package com.adarena.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj de la aplicación. Los servicios piden la hora a este bean en vez de llamar a
 * {@code Instant.now()}: así los tests pueden "viajar en el tiempo" (clave para probar cierres
 * de subasta, anti-sniping o caducidades sin esperar de verdad).
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
