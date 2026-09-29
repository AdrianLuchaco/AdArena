package com.adarena.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Reloj de la aplicación. Los servicios piden la hora a este bean en vez de llamar a
 * {@code Instant.now()}: así los tests pueden "viajar en el tiempo" (clave para probar cierres
 * de subasta, anti-sniping o caducidades sin esperar de verdad).
 * <p>
 * Avanza de microsegundo en microsegundo, la misma precisión con la que PostgreSQL guarda las horas.
 * En Linux, Java da nanosegundos: sin esto, una hora recién calculada y la misma hora leída de la base
 * de datos no serían iguales.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
