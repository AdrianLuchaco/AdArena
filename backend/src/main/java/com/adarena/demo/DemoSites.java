package com.adarena.demo;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.service.ImageService;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewJson.GalleryImage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SOLO EN DESARROLLO. Las webs de los anunciantes de ejemplo (www.example.com/…) no existen, así
 * que aquí se crea "lo que habríamos leído de ellas": logo, foto principal (la de su anuncio), dos
 * fotos de ambiente y sus frases destacadas. Así se ve en local la presentación animada del ganador.
 * Se marcan como DEMO: nunca se intenta leerlas de internet.
 */
@Component
@Profile("dev")
public class DemoSites {

    /** Cómo es cada web de ejemplo (por el email del anunciante). */
    record Site(Color from, Color to, DemoImages.Mark mark, String headline, List<String> highlights) {
    }

    static final Map<String, Site> SITES = Map.of(
            "demo-cafe@adarena.local", new Site(new Color(0x7C2D12), new Color(0xF59E0B), DemoImages.Mark.RING,
                    "Specialty coffee, roasted in Madrid",
                    List.of("Roasted every week, in small batches", "Flat whites with farm milk",
                            "Brunch on Saturdays and Sundays", "Beans to brew at home")),
            "demo-bicis@adarena.local", new Site(new Color(0x064E3B), new Color(0x34D399), DemoImages.Mark.TRIANGLE,
                    "Electric and mountain bike rentals",
                    List.of("Rent by the hour or by the day", "24-hour express repairs",
                            "Guided rides in the mountains", "The latest e-bikes")),
            "demo-lumen@adarena.local", new Site(new Color(0x312E81), new Color(0xA78BFA), DemoImages.Mark.SQUARE,
                    "Product photography and portraits",
                    List.of("Studio or on-location shoots", "Photos ready for your online shop",
                            "Team portraits", "Delivered in 72 hours")),
            "demo-huerta@adarena.local", new Site(new Color(0x365314), new Color(0xBEF264), DemoImages.Mark.WAVE,
                    "Seasonal organic fruit and veg",
                    List.of("Straight from the farm to your door", "Weekly boxes, cancel anytime",
                            "Certified organic", "Seasonal recipes in every box")));

    private final SitePreviewRepository repository;
    private final ImageService imageService;
    private final SitePreviewJson json;

    public DemoSites(SitePreviewRepository repository, ImageService imageService, SitePreviewJson json) {
        this.repository = repository;
        this.imageService = imageService;
        this.json = json;
    }

    /** Crea la web de ejemplo de este anunciante si es uno de los de ejemplo y todavía no la tiene. */
    public Optional<SitePreview> ensure(String email, AdProfile profile, Instant now) {
        Site site = SITES.get(email);
        if (site == null) {
            return Optional.empty();
        }
        Optional<SitePreview> existing = repository.findByUrl(profile.getWebsiteUrl());
        if (existing.isPresent() && existing.get().getSource() == SitePreview.Source.DEMO) {
            // Ya existe: se actualizan sus textos (por si eran de una versión anterior)
            SitePreview preview = existing.get();
            preview.update(new SitePreview.Content(preview.getFinalUrl(), preview.isFrameable(), preview.getEmbedUrl(),
                    profile.getCompanyName(), site.headline(), profile.getDescription(), preview.getThemeColor(),
                    preview.getIconImageId(), preview.getIconSrc(), preview.getHeroImageId(), preview.getHeroSrc(),
                    preview.getGallery(), json.writeHighlights(site.highlights())), now);
            return Optional.of(repository.save(preview));
        }
        UUID owner = profile.getUserId();
        StoredImage icon = imageService.store(owner, DemoImages.renderIcon(site.from(), site.to(), site.mark()));
        List<GalleryImage> gallery = List.of(
                new GalleryImage("demo:scene:1",
                        imageService.store(owner, DemoImages.renderScene(site.from(), site.to(), owner.hashCode())).getId()),
                new GalleryImage("demo:scene:2",
                        imageService.store(owner, DemoImages.renderScene(site.to().darker(), site.from(), owner.hashCode() + 7L)).getId()));
        // Si el lector ya había intentado leer la dirección de verdad, se sustituye por la de ejemplo
        existing.ifPresent(repository::delete);
        repository.flush();
        SitePreview preview = new SitePreview(profile.getWebsiteUrl(), owner, SitePreview.Source.DEMO);
        preview.update(new SitePreview.Content(profile.getWebsiteUrl(), true, null, profile.getCompanyName(),
                site.headline(), profile.getDescription(), String.format("#%06x", site.to().getRGB() & 0xffffff),
                icon.getId(), "demo:icon", profile.getImageId(), "demo:hero",
                json.writeGallery(gallery), json.writeHighlights(site.highlights())), now);
        return Optional.of(repository.save(preview));
    }
}
