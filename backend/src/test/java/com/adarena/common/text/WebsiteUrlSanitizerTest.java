package com.adarena.common.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebsiteUrlSanitizerTest {

    @Test
    void addsHttpsWhenTheUserTypesJustTheDomain() {
        assertThat(WebsiteUrlSanitizer.sanitize("  miweb.es  ")).isEqualTo("https://miweb.es");
    }

    @Test
    void normalizesTheHostButKeepsThePath() {
        assertThat(WebsiteUrlSanitizer.sanitize("https://WWW.Example.COM/Tienda?ref=adarena#ofertas"))
                .isEqualTo("https://www.example.com/Tienda?ref=adarena#ofertas");
    }

    @Test
    void convertsInternationalDomainsToPunycode() {
        assertThat(WebsiteUrlSanitizer.sanitize("https://españa.es/")).isEqualTo("https://xn--espaa-rta.es/");
    }

    @Test
    void dropsTheDefaultPort() {
        assertThat(WebsiteUrlSanitizer.sanitize("https://example.com:443/a")).isEqualTo("https://example.com/a");
        assertThat(WebsiteUrlSanitizer.sanitize("https://example.com:8443/a")).isEqualTo("https://example.com:8443/a");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",               // sin cifrar
            "javascript:alert(1)",              // no es una web
            "ftp://example.com",
            "https://localhost",
            "https://192.168.1.10/admin",       // IP privada
            "https://8.8.8.8",                  // IP pública
            "https://[::1]/",                   // IPv6
            "https://usuario:clave@example.com", // truco de phishing
            "https://intranet",                 // sin dominio de primer nivel
            "https://mi-nas.local",
            "https://exa mple.com",             // espacios
            "https://-mal-.com",
            ""
    })
    void rejectsUnsafeOrInvalidUrls(String url) {
        assertThatThrownBy(() -> WebsiteUrlSanitizer.sanitize(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("(?s).*(website|domain).*"); // mensaje (en inglés) para el usuario
    }

    @Test
    void rejectsVeryLongUrls() {
        String longUrl = "https://example.com/" + "a".repeat(WebsiteUrlSanitizer.MAX_LENGTH);

        assertThatThrownBy(() -> WebsiteUrlSanitizer.sanitize(longUrl)).hasMessageContaining("too long");
    }
}
