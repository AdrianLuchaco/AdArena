package com.adarena.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

    @Test
    void singleLineCollapsesSpacesAndRemovesControlCharacters() {
        assertThat(TextSanitizer.singleLine("  Café\tAurora\n\u0007 SL  ")).isEqualTo("Café Aurora SL");
    }

    @Test
    void removesInvisibleDirectionTricks() {
        // U+202E invierte el texto en pantalla: se usa para disfrazar nombres
        assertThat(TextSanitizer.singleLine("Tienda‮abc​")).isEqualTo("Tiendaabc");
    }

    @Test
    void keepsEmojisBuiltWithTheInvisibleJoiner() {
        String womanTechnologist = "👩‍💻";
        assertThat(TextSanitizer.singleLine("Hola " + womanTechnologist)).isEqualTo("Hola " + womanTechnologist);
    }

    @Test
    void multiLineKeepsParagraphsButNotHugeGaps() {
        assertThat(TextSanitizer.multiLine("  Línea 1  \r\n\r\n\r\n\r\n   Línea   2 \n"))
                .isEqualTo("Línea 1\n\nLínea 2");
    }

    @Test
    void normalizesUnicode() {
        String decomposed = "Café"; // "e" + tilde combinada
        assertThat(TextSanitizer.singleLine(decomposed)).isEqualTo("Café");
    }
}
