package com.adarena.site.service;

import com.adarena.common.config.AppProperties;
import com.adarena.image.service.ImageService;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.dto.SiteDtos.Showcase;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import com.adarena.site.dto.SiteDtos.ViewMode;
import com.adarena.site.extract.EmbedLinks;
import com.adarena.site.repository.SitePreviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la web muestra de la web de cada proyecto: cómo se ve en el visor ({@link SiteInfo}) y la
 * presentación animada del ganador ({@link Showcase}). Solo lee de la base de datos; si una web
 * no se ha leído nunca o hace mucho, pide que se lea en segundo plano.
 */
@Service
public class SitePreviewService {

    private final SitePreviewRepository repository;
    private final SitePreviewRefresher refresher;
    private final SitePreviewJson json;
    private final AppProperties properties;
    private final Clock clock;

    public SitePreviewService(SitePreviewRepository repository, SitePreviewRefresher refresher,
                              SitePreviewJson json, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.refresher = refresher;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    /** Cómo se ve una web en el visor. {@code ownerId}: quien la promociona (por si hay que leerla). */
    @Transactional(readOnly = true)
    public SiteInfo siteInfo(String url, UUID ownerId) {
        return siteInfos(Map.of(url, ownerId)).get(url);
    }

    /** Lo mismo para varias webs a la vez (una sola consulta). Clave: dirección; valor: quién la usa. */
    @Transactional(readOnly = true)
    public Map<String, SiteInfo> siteInfos(Map<String, UUID> owners) {
        Map<String, SitePreview> previews = new HashMap<>();
        if (!owners.isEmpty()) {
            repository.findByUrlIn(owners.keySet()).forEach(preview -> previews.put(preview.getUrl(), preview));
        }
        Map<String, SiteInfo> result = new HashMap<>();
        owners.forEach((url, ownerId) -> {
            SitePreview preview = previews.get(url);
            refreshIfStale(url, ownerId, preview);
            result.put(url, toSiteInfo(url, preview));
        });
        return result;
    }

    /** La presentación de una web, si ya la hemos leído bien. */
    @Transactional(readOnly = true)
    public Optional<Showcase> showcase(String url) {
        return repository.findByUrl(url).filter(SitePreview::isReady).map(preview -> toShowcase(url, preview));
    }

    @Transactional(readOnly = true)
    public Optional<SitePreview> find(String url) {
        return repository.findByUrl(url);
    }

    /** Pide que se lea la web si nunca se leyó o si la lectura tiene más de 20 h. */
    public void refreshIfStale(String url, UUID ownerId, SitePreview preview) {
        Instant fetchedAt = preview == null ? null : preview.getFetchedAt();
        boolean demo = preview != null && preview.getSource() == SitePreview.Source.DEMO;
        if (!demo && (fetchedAt == null || fetchedAt.isBefore(clock.instant().minus(properties.previews().maxAge())))) {
            refresher.request(url, ownerId);
        }
    }

    // ------------------------------------------------------------------ conversión

    public SiteInfo toSiteInfo(String url, SitePreview preview) {
        String domain = domainOf(url);
        Optional<String> embed = EmbedLinks.embedUrl(url);
        if (preview == null || !preview.isReady()) {
            return new SiteInfo(embed.isPresent() ? ViewMode.FRAME : ViewMode.WINDOW, embed.orElse(null), url,
                    domain, domain, null, null, null, null, null);
        }
        boolean frame = preview.isFrameable() || preview.getEmbedUrl() != null;
        String frameUrl = preview.getEmbedUrl() != null ? preview.getEmbedUrl()
                : preview.getFinalUrl() != null ? preview.getFinalUrl() : url;
        return new SiteInfo(frame ? ViewMode.FRAME : ViewMode.WINDOW, frame ? frameUrl : null, url, domain,
                preview.getSiteName() != null ? preview.getSiteName() : domain, preview.getTitle(),
                preview.getDescription(), ImageService.publicUrl(preview.getIconImageId()),
                ImageService.publicUrl(preview.getHeroImageId()), preview.getThemeColor());
    }

    public Showcase toShowcase(String url, SitePreview preview) {
        List<String> gallery = json.readGallery(preview.getGallery()).stream()
                .map(photo -> ImageService.publicUrl(photo.imageId()))
                .toList();
        String domain = domainOf(url);
        return new Showcase(preview.getSiteName() != null ? preview.getSiteName() : domain, domain,
                preview.getTitle(), preview.getDescription(), preview.getThemeColor(),
                ImageService.publicUrl(preview.getIconImageId()), ImageService.publicUrl(preview.getHeroImageId()),
                gallery, json.readHighlights(preview.getHighlights()));
    }

    /** "https://www.cafe-aurora.es/carta" → "cafe-aurora.es". */
    public static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return url;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
