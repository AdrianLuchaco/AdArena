package com.adarena.site.extract;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ¿Deja una web que la mostremos dentro de AdArena? Lo decide ella con sus cabeceras. */
class FramePolicyTest {

    private static final List<String> US = List.of("https://adarena.com", "http://localhost:3000");

    private static boolean frameable(Map<String, List<String>> headers) {
        return FramePolicy.allowsFraming(headers, US);
    }

    @Test
    void withoutRestrictionsItCanBeShown() {
        assertThat(frameable(Map.of())).isTrue();
        assertThat(frameable(Map.of("content-security-policy", List.of("default-src 'self'; img-src *")))).isTrue();
    }

    @Test
    void xFrameOptionsDenyOrSameOriginBlockIt() {
        assertThat(frameable(Map.of("x-frame-options", List.of("DENY")))).isFalse();
        assertThat(frameable(Map.of("x-frame-options", List.of("sameorigin")))).isFalse();
        // Valores que los navegadores ignoran
        assertThat(frameable(Map.of("x-frame-options", List.of("ALLOWALL")))).isTrue();
    }

    @Test
    void frameAncestorsDecidesWhenPresent() {
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors 'none'")))).isFalse();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors 'self'")))).isFalse();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors *")))).isTrue();
        assertThat(frameable(Map.of("content-security-policy", List.of("default-src 'self'; frame-ancestors https:")))).isTrue();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors 'self' https://adarena.com")))).isTrue();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors https://*.adarena.com")))).isFalse();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors *.adarena.com adarena.com")))).isTrue();
        assertThat(frameable(Map.of("content-security-policy", List.of("frame-ancestors https://otra-web.com")))).isFalse();
    }

    @Test
    void frameAncestorsWinsOverXFrameOptionsAndEveryPolicyMustAllowIt() {
        assertThat(frameable(Map.of(
                "content-security-policy", List.of("frame-ancestors *"),
                "x-frame-options", List.of("DENY")))).isTrue();
        assertThat(frameable(Map.of("content-security-policy",
                List.of("frame-ancestors *", "frame-ancestors 'self'")))).isFalse();
        assertThat(frameable(Map.of("content-security-policy",
                List.of("frame-ancestors *, frame-ancestors 'none'")))).isFalse();
    }
}
