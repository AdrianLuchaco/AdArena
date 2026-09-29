package com.adarena.site.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.earn.domain.SocialPlatform;
import com.adarena.image.service.ImageProcessor;
import com.adarena.image.service.ImageService;
import com.adarena.image.service.ProcessedImage;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.extract.DominantColor;
import com.adarena.site.extract.EmbedLinks;
import com.adarena.site.extract.FramePolicy;
import com.adarena.site.extract.SiteExtractor;
import com.adarena.site.extract.SiteExtractor.ExtractedSite;
import com.adarena.site.fetch.SiteFetcher;
import com.adarena.site.fetch.SiteFetcher.FetchException;
import com.adarena.site.fetch.SiteFetcher.FetchedPage;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson.GalleryImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lee la web de un proyecto y guarda lo que encuentra ({@link SitePreview}). Se ejecuta en segundo
 * plano ({@link SitePreviewRefresher}): nunca hace esperar a quien está usando LaunchCrown.
 * <p>
 * Las imágenes se descargan (máx. 5 MB), se sanean igual que las que suben los usuarios (se
 * vuelven a codificar desde cero) y se guardan en LaunchCrown: nunca enlazamos imágenes de otras webs.
 * Si una foto no ha cambiado desde la última lectura, se reutiliza en vez de descargarla otra vez.
 */
@Service
public class SitePreviewUpdater {

    private static final Logger log = LoggerFactory.getLogger(SitePreviewUpdater.class);

    /** Las fotos de la presentación tienen que verse bien a pantalla completa. */
    static final int MIN_PHOTO_SIDE = 320;
    static final int MAX_GALLERY = 3;
    /** Tiempo máximo para descargar imágenes en cada lectura. */
    private static final Duration IMAGE_BUDGET = Duration.ofSeconds(25);

    private final SitePreviewRepository repository;
    private final SiteFetcher fetcher;
    private final ImageProcessor imageProcessor;
    private final ImageService imageService;
    private final SitePreviewJson json;
    private final AppProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SitePreviewUpdater(SitePreviewRepository repository, SiteFetcher fetcher, ImageProcessor imageProcessor,
                              ImageService imageService, SitePreviewJson json, AppProperties properties,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.fetcher = fetcher;
        this.imageProcessor = imageProcessor;
        this.imageService = imageService;
        this.json = json;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Lee la web si hace falta: si nunca se leyó o si la última lectura tiene más de {@code maxAge}.
     * Nunca más de una vez cada {@code refresh-cooldown} (2 min), aunque lo pida su dueño.
     *
     * @return true si se ha leído (bien o mal)
     */
    public boolean refresh(String url, UUID ownerId, Duration maxAge) {
        if (!properties.previews().enabled()) {
            return false;
        }
        Instant now = clock.instant();
        SitePreview existing = repository.findByUrl(url).orElse(null);
        if (existing != null) {
            if (existing.getSource() == SitePreview.Source.DEMO) {
                return false;
            }
            Instant last = existing.getFetchedAt();
            if (last != null && (last.isAfter(now.minus(maxAge))
                    || last.isAfter(now.minus(properties.previews().refreshCooldown())))) {
                return false;
            }
        }
        UUID imageOwner = existing != null ? existing.getOwnerId() : ownerId;
        SitePreview.Content content = null;
        String error = null;
        try {
            content = read(url, imageOwner, existing);
        } catch (FetchException e) {
            error = e.getMessage();
        } catch (RuntimeException e) {
            log.warn("Unexpected error reading {}", url, e);
            error = "Unexpected error while reading the website";
        }
        if (error != null) {
            log.info("Could not read {}: {}", url, error);
        }
        save(url, imageOwner, content, error, now);
        return true;
    }

    // ------------------------------------------------------------------ leer

    SitePreview.Content read(String url, UUID ownerId, SitePreview previous) {
        Optional<String> embed = EmbedLinks.embedUrl(url);
        SocialPlatform platform = SocialPlatform.fromUrl(url);
        if (platform != SocialPlatform.WEB && platform != SocialPlatform.GITHUB) {
            // Las redes sociales no se dejan mostrar dentro de otras webs ni leer sin cuenta. Nos
            // quedamos con lo que dice la propia dirección (y su reproductor, si es un vídeo de YouTube).
            return new SitePreview.Content(url, embed.isPresent(), embed.orElse(null), platform.getLabel(),
                    handleOf(url), null, null, null, null, null, null, "[]", "[]");
        }

        FetchedPage page = fetcher.fetchPage(url);
        if (!page.isOk()) {
            throw new FetchException("The website answered with status " + page.status());
        }
        if (!page.isHtml()) {
            throw new FetchException("The address is not a web page");
        }
        ExtractedSite site = SiteExtractor.extract(page.body(), page.contentType(), page.finalUrl());
        boolean frameable = page.finalUrl().startsWith("https://")
                && FramePolicy.allowsFraming(page.headers(), properties.frontendOrigins());

        Images images = new Images(ownerId, previous, clock.instant().plus(IMAGE_BUDGET));
        Stored icon = null;
        for (String source : site.iconSources()) {
            icon = images.get(source, 64);
            if (icon != null) {
                break;
            }
        }
        Stored hero = site.heroSource() == null ? null : images.get(site.heroSource(), MIN_PHOTO_SIDE);
        List<Stored> gallery = new ArrayList<>();
        for (String source : site.imageSources()) {
            if (gallery.size() >= MAX_GALLERY) {
                break;
            }
            Stored photo = images.get(source, MIN_PHOTO_SIDE);
            if (photo != null && (hero == null || !photo.imageId().equals(hero.imageId()))) {
                gallery.add(photo);
            }
        }
        if (hero == null && !gallery.isEmpty()) {
            hero = gallery.removeFirst();
        }

        String themeColor = site.themeColor();
        if (themeColor == null && hero != null) {
            themeColor = DominantColor.of(images.bytes(hero));
        }
        if (themeColor == null && icon != null) {
            themeColor = DominantColor.of(images.bytes(icon));
        }

        return new SitePreview.Content(page.finalUrl(), frameable || embed.isPresent(), embed.orElse(null),
                site.siteName(), site.title(), site.description(), themeColor,
                icon == null ? null : icon.imageId(), icon == null ? null : icon.source(),
                hero == null ? null : hero.imageId(), hero == null ? null : hero.source(),
                json.writeGallery(gallery.stream().map(photo -> new GalleryImage(photo.source(), photo.imageId())).toList()),
                json.writeHighlights(site.highlights()));
    }

    /** "https://www.instagram.com/martacocina/" → "@martacocina". */
    static String handleOf(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (path == null) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || segment.equals("in") || segment.equals("company") || segment.equals("c")
                    || segment.equals("channel") || segment.equals("user")) {
                continue;
            }
            if (segment.equals("watch") || segment.equals("shorts") || segment.equals("live")) {
                return null;  // un vídeo: su título lo pone quien lo promociona
            }
            String handle = segment.length() > 60 ? segment.substring(0, 60) : segment;
            return handle.startsWith("@") ? handle : "@" + handle;
        }
        return null;
    }

    /** Una imagen ya guardada en LaunchCrown y la dirección de la que salió. */
    private record Stored(String source, UUID imageId, byte[] data) {
    }

    /** Descargas de imágenes de una lectura: reutiliza las que no han cambiado y respeta el tiempo máximo. */
    private final class Images {
        private final UUID ownerId;
        private final Map<String, UUID> previous = new HashMap<>();
        private final Instant deadline;

        Images(UUID ownerId, SitePreview previousPreview, Instant deadline) {
            this.ownerId = ownerId;
            this.deadline = deadline;
            if (previousPreview != null) {
                if (previousPreview.getIconSrc() != null && previousPreview.getIconImageId() != null) {
                    previous.put(previousPreview.getIconSrc(), previousPreview.getIconImageId());
                }
                if (previousPreview.getHeroSrc() != null && previousPreview.getHeroImageId() != null) {
                    previous.put(previousPreview.getHeroSrc(), previousPreview.getHeroImageId());
                }
                for (GalleryImage photo : json.readGallery(previousPreview.getGallery())) {
                    previous.put(photo.src(), photo.imageId());
                }
            }
        }

        /** @return la imagen guardada, o null si no se puede descargar, no es válida o es demasiado pequeña */
        Stored get(String source, int minSide) {
            UUID reused = previous.get(source);
            if (reused != null) {
                return new Stored(source, reused, null);
            }
            if (clock.instant().isAfter(deadline)) {
                return null;
            }
            try {
                ProcessedImage image = imageProcessor.process(fetcher.fetchImage(source));
                if (Math.max(image.width(), image.height()) < minSide) {
                    return null;
                }
                UUID id = imageService.store(ownerId, image).getId();
                previous.put(source, id);
                return new Stored(source, id, image.data());
            } catch (FetchException | ApiException e) {
                return null;
            }
        }

        byte[] bytes(Stored stored) {
            return stored.data() != null ? stored.data() : imageService.get(stored.imageId()).getData();
        }
    }

    // ------------------------------------------------------------------ guardar

    private void save(String url, UUID ownerId, SitePreview.Content content, String error, Instant now) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                transaction.executeWithoutResult(status -> {
                    SitePreview preview = repository.findByUrl(url)
                            .orElseGet(() -> new SitePreview(url, ownerId, SitePreview.Source.WEB));
                    if (content != null) {
                        preview.update(content, now);
                    } else {
                        preview.markFailed(error, now);
                    }
                    repository.saveAndFlush(preview);
                });
                return;
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
                // Otra copia del backend la estaba guardando a la vez: se reintenta sobre su fila
                log.debug("Concurrent update of preview {}, retrying", url);
            }
        }
    }
}
