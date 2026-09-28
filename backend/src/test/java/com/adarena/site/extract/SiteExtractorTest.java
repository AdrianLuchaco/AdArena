package com.adarena.site.extract;

import com.adarena.site.extract.SiteExtractor.ExtractedSite;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué sacamos del HTML de una web para la presentación del ganador. */
class SiteExtractorTest {

    private static final String BASE = "https://www.cafe-aurora.es/";

    private static final String HTML = """
            <!doctype html>
            <html lang="es"><head>
              <meta charset="utf-8">
              <title>Café Aurora | Café de especialidad en Madrid</title>
              <meta name="description" content="Tostamos café de especialidad cada semana.">
              <meta property="og:title" content="Café Aurora: café de especialidad tostado en Madrid">
              <meta property="og:site_name" content="Café Aurora">
              <meta property="og:image" content="/img/portada.jpg">
              <meta name="theme-color" content="#F59E0B">
              <link rel="icon" href="/favicon-32.png" sizes="32x32">
              <link rel="apple-touch-icon" href="/icono-180.png" sizes="180x180">
              <link rel="icon" href="/logo.svg" type="image/svg+xml">
            </head>
            <body>
              <header><nav><h2>Menú principal</h2></nav><img src="/logo-cabecera.png"></header>
              <div class="cookie-banner"><h3>Usamos cookies</h3></div>
              <main>
                <section><header><h1>Café de especialidad tostado en Madrid</h1></header>
                  <p>Somos una pequeña tostaduría de barrio con más de diez años de experiencia en café de especialidad.</p>
                </section>
                <h2>Tostamos cada semana</h2>
                <h2>Brunch los sábados y domingos</h2>
                <h2>Tostamos cada semana</h2>
                <h3>Café en grano para llevar</h3>
                <img src="https://www.cafe-aurora.es/img/barra.jpg" width="1200" height="800" alt="">
                <img data-src="/img/brunch.jpg" alt="">
                <img srcset="/img/grano-400.jpg 400w, /img/grano-1600.jpg 1600w" src="/img/grano-400.jpg">
                <img src="/img/pixel.gif" width="1" height="1">
                <img src="http://inseguro.es/foto.jpg">
                <img src="/iconos/icon-star.png">
              </main>
              <footer><h2>Contacto</h2></footer>
            </body></html>
            """;

    private static ExtractedSite extract(String html) {
        return SiteExtractor.extract(html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8", BASE);
    }

    @Test
    void readsTheImportantBits() {
        ExtractedSite site = extract(HTML);
        assertThat(site.siteName()).isEqualTo("Café Aurora");
        assertThat(site.title()).isEqualTo("Café Aurora: café de especialidad tostado en Madrid");
        assertThat(site.description()).isEqualTo("Tostamos café de especialidad cada semana.");
        assertThat(site.themeColor()).isEqualTo("#f59e0b");
        assertThat(site.heroSource()).isEqualTo("https://www.cafe-aurora.es/img/portada.jpg");
    }

    @Test
    void logosPreferTheBigAppleIconAndSkipSvg() {
        assertThat(extract(HTML).iconSources()).containsExactly(
                "https://www.cafe-aurora.es/icono-180.png",
                "https://www.cafe-aurora.es/favicon-32.png",
                "https://www.cafe-aurora.es/apple-touch-icon.png");
    }

    @Test
    void highlightsAreTheSectionTitlesWithoutMenusCookiesOrRepeats() {
        assertThat(extract(HTML).highlights()).containsExactly(
                "Café de especialidad tostado en Madrid",
                "Tostamos cada semana",
                "Brunch los sábados y domingos",
                "Café en grano para llevar");
    }

    @Test
    void photosAreBigHttpsImagesOfTheContent() {
        assertThat(extract(HTML).imageSources()).containsExactly(
                "https://www.cafe-aurora.es/img/barra.jpg",
                "https://www.cafe-aurora.es/img/brunch.jpg",
                "https://www.cafe-aurora.es/img/grano-1600.jpg");
    }

    @Test
    void aMainThatOnlyWrapsTheHeroIsNotTheWholeContent() {
        // Hay webs que solo meten la cabecera en <main>: el resto de secciones cuenta igual
        ExtractedSite site = extract("""
                <html><head><title>Estudio Norte</title></head><body>
                  <main><h1>Fotos que venden</h1></main>
                  <section><h2>Sesiones a domicilio</h2><p>Vamos a tu tienda con todo el equipo de iluminación.</p></section>
                  <section><h2 class="visually-hidden">Opiniones</h2>
                    <blockquote><h3>Un trabajo increíble, lo recomiendo a todo el mundo</h3></blockquote></section>
                  <section><h2>Entrega en 72 horas</h2><p>Recibes tus fotos editadas y listas para publicar en tu web.</p></section>
                </body></html>
                """);
        assertThat(site.highlights()).containsExactly("Fotos que venden", "Sesiones a domicilio", "Entrega en 72 horas");
    }

    @Test
    void worksWithAlmostEmptyPages() {
        ExtractedSite site = extract("<html><head><title>Hola</title></head><body><p>Poca cosa</p></body></html>");
        assertThat(site.title()).isEqualTo("Hola");
        assertThat(site.siteName()).isEqualTo("Hola");
        assertThat(site.description()).isNull();
        assertThat(site.themeColor()).isNull();
        assertThat(site.heroSource()).isNull();
        assertThat(site.imageSources()).isEmpty();
        assertThat(site.highlights()).isEmpty();
    }

    @Test
    void textIsCleanedAndCut() {
        String longTitle = "Muy ".repeat(80) + "largo";
        ExtractedSite site = extract("<html><head><title>" + longTitle + "</title>"
                + "<meta name=\"theme-color\" content=\"#ffffff\"></head><body>"
                + "<h2>  Espacios\n\t raros  </h2></body></html>");
        assertThat(site.title()).hasSizeLessThanOrEqualTo(SiteExtractor.MAX_TITLE).endsWith("…");
        assertThat(site.highlights()).containsExactly("Espacios raros");
        assertThat(site.themeColor()).as("un blanco no dice nada de la marca").isNull();
    }
}
