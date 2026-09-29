package com.adarena.site.extract;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enlaces que tienen una versión oficial "para incrustar". YouTube no deja mostrar su web dentro de
 * otras, pero sí su reproductor: si alguien promociona un VÍDEO, se ve dentro de LaunchCrown. (Los
 * canales y perfiles de redes sociales no tienen versión incrustable: se abren en una ventana.)
 */
public final class EmbedLinks {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern PATH_VIDEO = Pattern.compile("^/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})(?:[/?].*)?$");
    private static final Pattern QUERY_VIDEO = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");

    private EmbedLinks() {
    }

    /** "https://www.youtube.com/watch?v=abc…" → reproductor sin cookies de seguimiento (youtube-nocookie.com). */
    public static Optional<String> embedUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String id = null;
        if (host.equals("youtu.be")) {
            String candidate = path.startsWith("/") ? path.substring(1) : path;
            int slash = candidate.indexOf('/');
            candidate = slash >= 0 ? candidate.substring(0, slash) : candidate;
            id = VIDEO_ID.matcher(candidate).matches() ? candidate : null;
        } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
            Matcher byPath = PATH_VIDEO.matcher(path);
            if (byPath.matches()) {
                id = byPath.group(1);
            } else if (path.equals("/watch") && uri.getRawQuery() != null) {
                Matcher byQuery = QUERY_VIDEO.matcher(uri.getRawQuery());
                id = byQuery.find() ? byQuery.group(1) : null;
            }
        }
        return Optional.ofNullable(id).map(video -> "https://www.youtube-nocookie.com/embed/" + video + "?rel=0");
    }
}
