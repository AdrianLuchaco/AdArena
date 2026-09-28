package com.adarena.site.fetch;

import com.adarena.common.config.AppProperties;
import com.adarena.common.text.WebsiteUrlSanitizer;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee webs públicas con muchas precauciones, porque las direcciones las escriben los usuarios:
 * <ul>
 *   <li>Solo {@code https://}, solo dominios públicos y solo el puerto 443 (se comprueba en cada redirección).</li>
 *   <li>Solo se conecta a direcciones IP públicas ({@link PublicDnsResolver}): nada de la red interna.</li>
 *   <li>Como mucho 5 redirecciones, 5 s para conectar y 8 s sin recibir datos.</li>
 *   <li>Se leen como mucho 1,5 MB de HTML y 5 MB por imagen. Si la web manda más, se corta la conexión.</li>
 *   <li>Sin cookies, sin reintentos y sin enviar nada más que un GET.</li>
 * </ul>
 */
@Component
public class HttpSiteFetcher implements SiteFetcher, DisposableBean {

    static final int MAX_REDIRECTS = 5;
    static final int MAX_HTML_BYTES = 1_500_000;
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final CloseableHttpClient client;

    public HttpSiteFetcher(AppProperties properties) {
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PublicDnsResolver(SystemDefaultDnsResolver.INSTANCE))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(5))
                                .setSocketTimeout(Timeout.ofSeconds(8))
                                .build())
                        .setMaxConnTotal(16)
                        .setMaxConnPerRoute(4)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                        .setResponseTimeout(Timeout.ofSeconds(8))
                        .build())
                .setUserAgent(properties.previews().userAgent())
                .disableRedirectHandling()   // las seguimos a mano para comprobar cada destino
                .disableCookieManagement()
                .disableAutomaticRetries()
                .build();
    }

    @Override
    public FetchedPage fetchPage(String url) {
        Response response = get(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", MAX_HTML_BYTES, true);
        return new FetchedPage(response.url(), response.status(), response.headers(), response.contentType(),
                response.body());
    }

    @Override
    public byte[] fetchImage(String url) {
        Response response = get(url, "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8", MAX_IMAGE_BYTES, false);
        String type = response.contentType() == null ? "" : response.contentType().toLowerCase(Locale.ROOT);
        if (response.status() != 200 || !type.startsWith("image/")) {
            throw new FetchException("Not an image (HTTP " + response.status() + ", " + type + ")");
        }
        return response.body();
    }

    private record Response(String url, int status, Map<String, List<String>> headers, String contentType,
                            byte[] body, String location) {
    }

    private Response get(String startUrl, String accept, int maxBytes, boolean truncate) {
        String url = checkUrl(startUrl);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Response response = getOnce(url, accept, maxBytes, truncate);
            if (response.location() == null) {
                return response;
            }
            try {
                url = checkUrl(URI.create(url).resolve(response.location().trim()).toString());
            } catch (IllegalArgumentException e) {
                throw new FetchException("Invalid redirect: " + e.getMessage(), e);
            }
        }
        throw new FetchException("Too many redirects");
    }

    private Response getOnce(String url, String accept, int maxBytes, boolean truncate) {
        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", accept);
        request.setHeader("Accept-Language", "en-GB,en;q=0.9,es;q=0.6");
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            int status = response.getCode();
            Header location = response.getFirstHeader("Location");
            if (status >= 300 && status < 400 && location != null) {
                request.cancel();
                return new Response(url, status, Map.of(), null, new byte[0], location.getValue());
            }
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (Header header : response.getHeaders()) {
                headers.computeIfAbsent(header.getName().toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                        .add(header.getValue());
            }
            HttpEntity entity = response.getEntity();
            byte[] body = entity == null ? new byte[0] : readLimited(entity, request, maxBytes, truncate);
            return new Response(url, status, headers, entity == null ? null : entity.getContentType(), body, null);
        } catch (IOException e) {
            throw new FetchException("Could not read " + url + ": " + e.getMessage(), e);
        }
    }

    /** Lee como mucho {@code maxBytes}. Si hay más: corta (HTML) o falla (imágenes), y cierra la conexión. */
    private static byte[] readLimited(HttpEntity entity, HttpGet request, int maxBytes, boolean truncate) throws IOException {
        try (InputStream in = entity.getContent()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > maxBytes) {
                    request.cancel();  // no seguimos descargando lo que sobra
                    if (!truncate) {
                        throw new FetchException("Response larger than " + maxBytes + " bytes");
                    }
                    out.write(buffer, 0, maxBytes - out.size());
                    return out.toByteArray();
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /** Misma validación que las webs de los anunciantes, más: solo el puerto estándar de https. */
    static String checkUrl(String url) {
        String sanitized;
        try {
            sanitized = WebsiteUrlSanitizer.sanitize(url);
        } catch (IllegalArgumentException e) {
            throw new FetchException("URL not allowed: " + e.getMessage(), e);
        }
        URI uri;
        try {
            uri = URI.create(sanitized);
        } catch (IllegalArgumentException e) {
            throw new FetchException("Malformed URL", e);
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new FetchException("Only the standard https port is allowed");
        }
        // El fragmento (#...) nunca se envía al servidor
        int hash = sanitized.indexOf('#');
        return hash >= 0 ? sanitized.substring(0, hash) : sanitized;
    }

    @Override
    public void destroy() throws IOException {
        client.close();
    }
}
