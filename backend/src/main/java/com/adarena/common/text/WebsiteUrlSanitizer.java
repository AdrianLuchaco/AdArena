package com.adarena.common.text;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida y normaliza la web del anunciante. El botón "Visitar web" de la portada lleva a tus
 * visitantes allí, así que somos estrictos:
 * <ul>
 *   <li>Solo {@code https://}. Si el usuario escribe "miweb.com", se añade {@code https://}.</li>
 *   <li>Un dominio público de verdad: nada de IPs, "localhost" ni dominios internos.</li>
 *   <li>Sin usuario ni contraseña incrustados ({@code https://usuario:clave@...}), un truco de phishing.</li>
 *   <li>Dominios con tildes o eñes ("españa.es") se convierten a su forma técnica (punycode).</li>
 * </ul>
 */
public final class WebsiteUrlSanitizer {

    public static final int MAX_LENGTH = 2048;

    private static final Pattern URL = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)(.*)$", Pattern.DOTALL);
    private static final Pattern LABEL = Pattern.compile("^(?!-)[a-z0-9-]{1,63}(?<!-)$");
    private static final Pattern TLD = Pattern.compile("^([a-z]{2,63}|xn--[a-z0-9-]{1,59})$");
    private static final Pattern PORT = Pattern.compile("^\\d{1,5}$");

    private WebsiteUrlSanitizer() {
    }

    /**
     * @return la URL normalizada
     * @throws IllegalArgumentException con un mensaje en español listo para el usuario
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("The website is required.");
        }
        String url = raw.trim();
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        if (url.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("The website address can't contain spaces.");
        }
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("The website address is not valid.");
        }
        if (!"https".equalsIgnoreCase(matcher.group(1))) {
            throw new IllegalArgumentException("The website address must start with https://");
        }

        String authority = matcher.group(2);
        if (authority.contains("@")) {
            throw new IllegalArgumentException("The website address can't include a username or password.");
        }
        if (authority.startsWith("[")) {
            throw new IllegalArgumentException("Use your website's domain name, not an IP address.");
        }

        String host = authority;
        String port = null;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            host = authority.substring(0, colon);
            port = authority.substring(colon + 1);
            if (!PORT.matcher(port).matches() || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
                throw new IllegalArgumentException("The website address is not valid.");
            }
        }

        String asciiHost = toAsciiHost(host);
        String path = matcher.group(3);
        String candidate = "https://" + asciiHost + (port == null || port.equals("443") ? "" : ":" + port) + path;

        String normalized;
        try {
            normalized = new URI(candidate).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The website address contains invalid characters.");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The website address is too long.");
        }
        return normalized;
    }

    private static String toAsciiHost(String host) {
        String ascii;
        try {
            ascii = IDN.toASCII(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The website's domain is not valid.");
        }
        if (ascii.endsWith(".")) {
            ascii = ascii.substring(0, ascii.length() - 1);
        }
        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) {
            throw new IllegalArgumentException("The website's domain is not valid (example: mysite.com).");
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("The website's domain is not valid.");
            }
        }
        String tld = labels[labels.length - 1];
        if (!TLD.matcher(tld).matches()) {
            // Cubre también las IPv4 (1.2.3.4), cuyo último "trozo" es un número
            throw new IllegalArgumentException("Use your website's domain name, not an IP address.");
        }
        if (ascii.equals("localhost") || tld.equals("localhost") || tld.equals("local")
                || tld.equals("internal") || tld.equals("lan")) {
            throw new IllegalArgumentException("The website must be public.");
        }
        return ascii;
    }
}
