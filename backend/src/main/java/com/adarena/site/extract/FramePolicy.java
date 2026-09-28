package com.adarena.site.extract;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ¿Deja esta web que la mostremos dentro de AdArena (en un iframe)? Cada web lo decide con sus
 * cabeceras, y el navegador obedece:
 * <ul>
 *   <li>{@code Content-Security-Policy: frame-ancestors …}: la lista de webs que pueden mostrarla.
 *       Si existe, manda ella (los navegadores ignoran entonces X-Frame-Options).</li>
 *   <li>{@code X-Frame-Options: DENY | SAMEORIGIN}: nadie / solo ella misma.</li>
 * </ul>
 * Si no dice nada, se puede mostrar. Si no se puede, el visor la abre en una ventana aparte.
 */
public final class FramePolicy {

    private FramePolicy() {
    }

    /**
     * @param headers    cabeceras de la respuesta (nombres en minúsculas)
     * @param ourOrigins los orígenes de AdArena (p. ej. https://adarena.com)
     */
    public static boolean allowsFraming(Map<String, List<String>> headers, Collection<String> ourOrigins) {
        Boolean byCsp = null;
        for (String header : headers.getOrDefault("content-security-policy", List.of())) {
            // Una cabecera puede llevar varias políticas separadas por comas: hay que cumplirlas todas
            for (String policy : header.split(",")) {
                for (String directive : policy.split(";")) {
                    String[] tokens = directive.trim().split("\\s+");
                    if (tokens.length == 0 || !"frame-ancestors".equalsIgnoreCase(tokens[0])) {
                        continue;
                    }
                    boolean allowed = allowsUs(tokens, ourOrigins);
                    byCsp = byCsp == null ? allowed : byCsp && allowed;
                }
            }
        }
        if (byCsp != null) {
            return byCsp;
        }
        for (String header : headers.getOrDefault("x-frame-options", List.of())) {
            for (String value : header.split(",")) {
                String option = value.trim().toUpperCase(Locale.ROOT);
                if (option.equals("DENY") || option.equals("SAMEORIGIN")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allowsUs(String[] tokens, Collection<String> ourOrigins) {
        for (int i = 1; i < tokens.length; i++) {
            String source = tokens[i].toLowerCase(Locale.ROOT);
            if (source.equals("*") || source.equals("https:")) {
                return true;
            }
            if (source.startsWith("'")) {
                continue;  // 'none', 'self'…: no somos nosotros
            }
            for (String origin : ourOrigins) {
                if (matchesOrigin(source, origin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "https://*.adarena.com", "adarena.com" o "https://adarena.com" frente a "https://adarena.com". */
    static boolean matchesOrigin(String source, String origin) {
        String ourHost;
        try {
            ourHost = URI.create(origin).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (ourHost == null) {
            return false;
        }
        String host = source.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        int end = host.indexOf('/');
        host = end >= 0 ? host.substring(0, end) : host;
        int colon = host.indexOf(':');
        host = colon >= 0 ? host.substring(0, colon) : host;
        ourHost = ourHost.toLowerCase(Locale.ROOT);
        if (host.startsWith("*.")) {
            return ourHost.endsWith(host.substring(1));
        }
        return host.equals(ourHost);
    }
}
