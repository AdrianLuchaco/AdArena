package com.adarena.common.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Limpia los textos que escriben los usuarios antes de guardarlos:
 * <ul>
 *   <li>Normaliza Unicode (una "é" siempre se guarda igual).</li>
 *   <li>Quita caracteres de control y caracteres invisibles que se usan para engañar (por
 *       ejemplo, los que invierten la dirección del texto). Se conserva el "unidor" invisible
 *       que forman algunos emojis.</li>
 *   <li>Colapsa espacios repetidos.</li>
 * </ul>
 * No hace falta "escapar HTML": el frontend (React) siempre muestra los textos como texto, nunca como HTML.
 */
public final class TextSanitizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B\\u200C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    private static final Pattern CONTROL_EXCEPT_NEWLINE = Pattern.compile("[\\p{Cc}&&[^\\n]]");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cc}");
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern ANY_SPACES = Pattern.compile("\\s+");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");

    private TextSanitizer() {
    }

    /** Texto de una sola línea (nombres, títulos). */
    public static String singleLine(String input) {
        if (input == null) {
            return null;
        }
        String text = Normalizer.normalize(input, Normalizer.Form.NFC);
        text = INVISIBLE.matcher(text).replaceAll("");
        text = CONTROL.matcher(text).replaceAll(" ");
        return ANY_SPACES.matcher(text).replaceAll(" ").trim();
    }

    /** Texto que admite saltos de línea (descripciones). Como mucho una línea en blanco seguida. */
    public static String multiLine(String input) {
        if (input == null) {
            return null;
        }
        String text = Normalizer.normalize(input, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        text = INVISIBLE.matcher(text).replaceAll("");
        text = CONTROL_EXCEPT_NEWLINE.matcher(text).replaceAll(" ");
        text = HORIZONTAL_SPACES.matcher(text).replaceAll(" ");
        text = text.lines().map(String::trim).reduce((a, b) -> a + "\n" + b).orElse("");
        return MANY_NEWLINES.matcher(text).replaceAll("\n\n").trim();
    }
}
