package com.adarena.site.fetch;

import com.adarena.site.fetch.SiteFetcher.FetchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué direcciones puede leer AdArena (se comprueba al empezar y en CADA redirección). */
class HttpSiteFetcherUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.ejemplo.es",              // sin cifrar
            "ftp://ejemplo.es/archivo",
            "https://127.0.0.1/",                 // IP en vez de dominio
            "https://[::1]/",
            "https://localhost/admin",
            "https://intranet.local/",
            "https://usuario:clave@ejemplo.es/",  // truco de phishing
            "https://ejemplo.es:8080/",           // solo el puerto estándar de https
            "https://ejemplo.es:22/",
            "javascript:alert(1)"
    })
    void rejectsAnythingButPublicHttpsOnTheStandardPort(String url) {
        assertThatThrownBy(() -> HttpSiteFetcher.checkUrl(url)).isInstanceOf(FetchException.class);
    }

    @Test
    void acceptsPublicHttpsAndDropsTheFragment() {
        assertThat(HttpSiteFetcher.checkUrl("https://www.cafe-aurora.es/carta?dia=hoy#postres"))
                .isEqualTo("https://www.cafe-aurora.es/carta?dia=hoy");
        assertThat(HttpSiteFetcher.checkUrl("https://ejemplo.es:443/")).startsWith("https://ejemplo.es");
    }
}
