package com.adarena.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Lo que la web muestra de las webs de los proyectos. */
public final class SiteDtos {

    private SiteDtos() {
    }

    @Schema(description = """
            FRAME: la web se ve dentro de LaunchCrown (iframe) y los puntos cuentan mientras la miras.
            WINDOW: la web no se deja mostrar dentro de otras; se abre en una ventana aparte y los
            puntos cuentan mientras esa ventana está abierta y estás en ella.""")
    public enum ViewMode { FRAME, WINDOW }

    /**
     * Una web en el visor y en las tarjetas.
     *
     * @param frameUrl dirección para el iframe (solo FRAME): la web o su reproductor oficial
     * @param openUrl  dirección para abrirla aparte (siempre)
     * @param imageUrl su foto principal (leída de la web), si la hay
     */
    public record SiteInfo(ViewMode mode, String frameUrl, String openUrl, String domain, String siteName,
                           String title, String description, String iconUrl, String imageUrl, String themeColor) {
    }

    /**
     * La presentación animada del ganador en la portada, montada con lo que hemos leído de su web.
     *
     * @param highlights frases destacadas (títulos de las secciones de su web)
     */
    public record Showcase(String siteName, String domain, String title, String description, String themeColor,
                           String iconUrl, String heroImageUrl, List<String> galleryUrls, List<String> highlights) {
    }

    @Schema(description = """
            NONE: todavía no tienes anuncio. PENDING: la estamos leyendo (vuelve a preguntar en unos
            segundos). READY: presentación lista. FAILED: no hemos podido leer tu web.""")
    public enum ShowcaseStatus { NONE, PENDING, READY, FAILED }

    /**
     * "Así se verá tu anuncio si ganas" en Mi anuncio.
     *
     * @param canRefreshAt desde cuándo puedes pedir que la volvamos a leer
     */
    public record MyShowcase(ShowcaseStatus status, Showcase showcase, String websiteUrl, Instant fetchedAt,
                             String error, Instant canRefreshAt) {
    }
}
