package com.adarena.site.fetch;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee páginas e imágenes de webs públicas. En producción, {@link HttpSiteFetcher}; en los tests,
 * uno falso que nunca sale a internet.
 */
public interface SiteFetcher {

    /** La página HTML (siguiendo redirecciones, solo https). */
    FetchedPage fetchPage(String url);

    /** Una imagen (máx. 5 MB). */
    byte[] fetchImage(String url);

    /**
     * Una página leída.
     *
     * @param finalUrl dónde acabó tras las redirecciones
     * @param headers  cabeceras con el nombre en minúsculas
     */
    record FetchedPage(String finalUrl, int status, Map<String, List<String>> headers, String contentType,
                       byte[] body) {

        public List<String> header(String name) {
            return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
        }

        public boolean isHtml() {
            String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            return type.startsWith("text/html") || type.startsWith("application/xhtml+xml");
        }

        public boolean isOk() {
            return status >= 200 && status < 300;
        }
    }

    /** No se ha podido leer (web caída, dirección interna, demasiado grande…). */
    class FetchException extends RuntimeException {
        public FetchException(String message) {
            super(message);
        }

        public FetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
