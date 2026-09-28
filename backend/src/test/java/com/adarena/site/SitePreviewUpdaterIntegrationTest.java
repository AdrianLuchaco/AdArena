package com.adarena.site;

import com.adarena.common.config.AppProperties;
import com.adarena.image.service.ImageProcessor;
import com.adarena.image.service.ImageService;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.dto.SiteDtos.Showcase;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import com.adarena.site.dto.SiteDtos.ViewMode;
import com.adarena.site.fetch.SiteFetcher;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewService;
import com.adarena.site.service.SitePreviewUpdater;
import com.adarena.support.IntegrationTest;
import com.adarena.support.TestImages;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lectura de la web de un proyecto, de principio a fin, con un lector FALSO (los tests nunca salen
 * a internet): qué se guarda, cuándo se vuelve a leer, qué pasa si la web falla y cómo se ve luego
 * en el visor y en la presentación del ganador.
 */
@IntegrationTest
class SitePreviewUpdaterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

    @Autowired SitePreviewRepository repository;
    @Autowired SitePreviewService previewService;
    @Autowired SitePreviewJson json;
    @Autowired ImageProcessor imageProcessor;
    @Autowired ImageService imageService;
    @Autowired AppProperties properties;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserRepository userRepository;

    FakeFetcher fetcher;
    UUID owner;
    String url;

    @BeforeEach
    void setUp() {
        fetcher = new FakeFetcher();
        owner = userRepository.save(new User(UUID.randomUUID() + "@test.dev", "hash", "Dueña", Role.USER, "2026-09",
                Instant.now())).getId();
        url = "https://www.cafe-" + UUID.randomUUID() + ".es/";
    }

    /** El lector de webs con la lectura ACTIVADA (en los tests está apagada) y un reloj fijo. */
    private SitePreviewUpdater updaterAt(Instant now) {
        AppProperties p = properties;
        AppProperties enabled = new AppProperties(p.frontendOrigins(), p.publicUrl(), p.jwt(), p.refreshCookie(),
                p.legal(), p.rateLimit(), p.jobs(), p.mail(), p.rewards(),
                new AppProperties.Previews(true, Duration.ofHours(20), Duration.ofMinutes(2), "AdArenaBot/test"),
                p.admin());
        return new SitePreviewUpdater(repository, fetcher, imageProcessor, imageService, json, enabled,
                transactionManager, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void servePage(String html, Map<String, List<String>> headers) {
        fetcher.pages.put(url, new SiteFetcher.FetchedPage(url, 200, headers, "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)));
    }

    private String site(String themeColor) {
        return """
                <html><head>
                  <title>Café Aurora | Madrid</title>
                  <meta property="og:title" content="Café de especialidad en Madrid">
                  <meta property="og:description" content="Tostamos cada semana.">
                  <meta property="og:image" content="%1$simg/portada.jpg">
                  %2$s
                  <link rel="apple-touch-icon" href="/icono.png" sizes="180x180">
                </head><body><main>
                  <h2>Tostamos cada semana</h2><h2>Brunch los domingos</h2>
                  <img src="%1$simg/barra.jpg"><img src="%1$simg/brunch.jpg"><img src="%1$simg/miniatura.jpg">
                </main></body></html>
                """.formatted(url, themeColor == null ? "" : "<meta name=\"theme-color\" content=\"" + themeColor + "\">");
    }

    private void serveImages() {
        fetcher.images.put(url + "img/portada.jpg", TestImages.opaquePng(1200, 800));
        fetcher.images.put(url + "icono.png", TestImages.opaquePng(180, 180));
        fetcher.images.put(url + "img/barra.jpg", TestImages.opaquePng(900, 600));
        fetcher.images.put(url + "img/brunch.jpg", TestImages.opaquePng(800, 800));
        fetcher.images.put(url + "img/miniatura.jpg", TestImages.opaquePng(120, 120)); // demasiado pequeña
    }

    @Test
    void readsTheWebsiteAndBuildsThePresentation() {
        servePage(site("#1F7A5A"), Map.of());
        serveImages();

        assertThat(updaterAt(T0).refresh(url, owner, Duration.ofHours(20))).isTrue();

        SitePreview preview = repository.findByUrl(url).orElseThrow();
        assertThat(preview.getStatus()).isEqualTo(SitePreview.Status.READY);
        assertThat(preview.isFrameable()).isTrue();
        assertThat(preview.getThemeColor()).isEqualTo("#1f7a5a");

        Showcase showcase = previewService.showcase(url).orElseThrow();
        assertThat(showcase.title()).isEqualTo("Café de especialidad en Madrid");
        assertThat(showcase.description()).isEqualTo("Tostamos cada semana.");
        assertThat(showcase.highlights()).containsExactly("Tostamos cada semana", "Brunch los domingos");
        assertThat(showcase.heroImageUrl()).startsWith("/api/public/images/");
        assertThat(showcase.iconUrl()).startsWith("/api/public/images/");
        assertThat(showcase.galleryUrls()).as("la miniatura de 120 px no sirve para pantalla completa").hasSize(2);
        assertThat(showcase.domain()).startsWith("cafe-");

        SiteInfo info = previewService.siteInfo(url, owner);
        assertThat(info.mode()).isEqualTo(ViewMode.FRAME);
        assertThat(info.frameUrl()).isEqualTo(url);
        assertThat(info.imageUrl()).isEqualTo(showcase.heroImageUrl());
    }

    @Test
    void withoutThemeColorItTakesTheMainColorOfThePhoto() {
        servePage(site(null), Map.of());
        serveImages();

        updaterAt(T0).refresh(url, owner, Duration.ofHours(20));

        // TestImages pinta en violeta (#6d28d9); la foto se vuelve a codificar, así que puede variar un poco
        String color = repository.findByUrl(url).orElseThrow().getThemeColor();
        assertThat(color).matches("^#[0-9a-f]{6}$");
        int rgb = Integer.parseInt(color.substring(1), 16);
        assertThat(Math.abs((rgb >> 16 & 0xff) - 0x6d)).isLessThan(8);
        assertThat(Math.abs((rgb & 0xff) - 0xd9)).isLessThan(8);
    }

    @Test
    void sitesThatForbidFramingOpenInAWindow() {
        servePage(site(null), Map.of("x-frame-options", List.of("SAMEORIGIN")));

        updaterAt(T0).refresh(url, owner, Duration.ofHours(20));

        SiteInfo info = previewService.siteInfo(url, owner);
        assertThat(info.mode()).isEqualTo(ViewMode.WINDOW);
        assertThat(info.frameUrl()).isNull();
        assertThat(info.openUrl()).isEqualTo(url);
    }

    @Test
    void recentReadsAreNotRepeatedAndUnchangedPhotosAreNotDownloadedAgain() {
        servePage(site(null), Map.of());
        serveImages();
        updaterAt(T0).refresh(url, owner, Duration.ofHours(20));
        int pages = fetcher.pageCalls.get();
        int images = fetcher.imageCalls.get();

        // Leída hace 1 minuto: ni aunque su dueño lo pida (espera mínima de 2 minutos)
        assertThat(updaterAt(T0.plus(Duration.ofMinutes(1))).refresh(url, owner, Duration.ZERO)).isFalse();
        // Leída hace 10 h: todavía vale
        assertThat(updaterAt(T0.plus(Duration.ofHours(10))).refresh(url, owner, Duration.ofHours(20))).isFalse();
        assertThat(fetcher.pageCalls.get()).isEqualTo(pages);

        // Al día siguiente se vuelve a leer, pero las fotos que no han cambiado no se descargan otra vez
        assertThat(updaterAt(T0.plus(Duration.ofHours(21))).refresh(url, owner, Duration.ofHours(20))).isTrue();
        assertThat(fetcher.pageCalls.get()).isEqualTo(pages + 1);
        assertThat(fetcher.imageCalls.get())
                .as("solo se reintenta la miniatura, que no se guardó por pequeña").isEqualTo(images + 1);
        assertThat(previewService.showcase(url).orElseThrow().galleryUrls()).hasSize(2);
    }

    @Test
    void ifTheWebsiteIsDownWeKeepWhatWeHad() {
        servePage(site(null), Map.of());
        updaterAt(T0).refresh(url, owner, Duration.ofHours(20));

        fetcher.pages.clear(); // la web se cae
        updaterAt(T0.plus(Duration.ofHours(21))).refresh(url, owner, Duration.ofHours(20));

        SitePreview preview = repository.findByUrl(url).orElseThrow();
        assertThat(preview.getStatus()).isEqualTo(SitePreview.Status.READY);
        assertThat(preview.getTitle()).isEqualTo("Café de especialidad en Madrid");
        assertThat(preview.getError()).isNotBlank();
        assertThat(preview.getFetchedAt()).isEqualTo(T0.plus(Duration.ofHours(21)));
    }

    @Test
    void aWebsiteThatNeverWorkedIsMarkedFailedAndOpensInAWindow() {
        updaterAt(T0).refresh(url, owner, Duration.ofHours(20));

        assertThat(repository.findByUrl(url).orElseThrow().getStatus()).isEqualTo(SitePreview.Status.FAILED);
        assertThat(previewService.showcase(url)).isEmpty();
        assertThat(previewService.siteInfo(url, owner).mode()).isEqualTo(ViewMode.WINDOW);
    }

    @Test
    void socialProfilesAreNeverReadAndYoutubeVideosUseTheirPlayer() {
        String instagram = "https://www.instagram.com/martacocina-" + UUID.randomUUID().toString().substring(0, 8) + "/";
        updaterAt(T0).refresh(instagram, owner, Duration.ofHours(20));
        assertThat(fetcher.pageCalls.get()).isZero();
        SiteInfo profile = previewService.siteInfo(instagram, owner);
        assertThat(profile.mode()).isEqualTo(ViewMode.WINDOW);
        assertThat(profile.siteName()).isEqualTo("Instagram");
        assertThat(profile.title()).startsWith("@martacocina-");

        String video = "https://www.youtube.com/watch?v=" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        updaterAt(T0).refresh(video, owner, Duration.ofHours(20));
        SiteInfo player = previewService.siteInfo(video, owner);
        assertThat(player.mode()).isEqualTo(ViewMode.FRAME);
        assertThat(player.frameUrl()).startsWith("https://www.youtube-nocookie.com/embed/");
        assertThat(fetcher.pageCalls.get()).isZero();
    }

    /** Lector de webs de mentira: devuelve lo que le digamos y cuenta las llamadas. */
    static final class FakeFetcher implements SiteFetcher {
        final Map<String, FetchedPage> pages = new HashMap<>();
        final Map<String, byte[]> images = new HashMap<>();
        final AtomicInteger pageCalls = new AtomicInteger();
        final AtomicInteger imageCalls = new AtomicInteger();

        @Override
        public FetchedPage fetchPage(String url) {
            pageCalls.incrementAndGet();
            FetchedPage page = pages.get(url);
            if (page == null) {
                throw new FetchException("No responde");
            }
            return page;
        }

        @Override
        public byte[] fetchImage(String url) {
            imageCalls.incrementAndGet();
            byte[] image = images.get(url);
            if (image == null) {
                throw new FetchException("No existe");
            }
            return image;
        }
    }
}
