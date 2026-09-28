package com.adarena.security;

import com.adarena.common.error.ProblemResponseWriter;
import com.adarena.common.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Límite de tamaño para las peticiones JSON (64 KB; la más grande real, un anuncio, ocupa ~3 KB).
 * Sin él, alguien podría enviar un JSON de cientos de megas para agotar la memoria del servidor.
 * Las subidas de imágenes (multipart) tienen su propio límite de 5 MB en application.yml.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_JSON_BYTES = 64 * 1024;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final ProblemResponseWriter writer;

    public RequestSizeLimitFilter(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    /** Solo las peticiones que llevan cuerpo (POST, PUT, PATCH) y no son subidas de archivos. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!BODY_METHODS.contains(request.getMethod())) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_JSON_BYTES) {
            writer.write(request, response, Problems.of(HttpStatus.CONTENT_TOO_LARGE, "REQUEST_TOO_LARGE",
                    "The request is too large."));
            return;
        }
        // Sin Content-Length (envío "por trozos"): se corta la lectura al pasar del límite
        chain.doFilter(request.getContentLengthLong() < 0 ? new LimitedRequest(request) : request, response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0 && ++read > MAX_JSON_BYTES) {
                        throw new IOException("Request body too large");
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = delegate.read(buffer, offset, length);
                    if (count > 0 && (read += count) > MAX_JSON_BYTES) {
                        throw new IOException("Request body too large");
                    }
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
