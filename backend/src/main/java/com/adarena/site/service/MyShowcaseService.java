package com.adarena.site.service;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.dto.SiteDtos.MyShowcase;
import com.adarena.site.dto.SiteDtos.ShowcaseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * "Así se verá tu anuncio si ganas": la presentación que montamos con la web del anunciante.
 * Puede pedir que la volvamos a leer (como mucho una vez cada 2 minutos).
 */
@Service
public class MyShowcaseService {

    private final AdProfileRepository profileRepository;
    private final SitePreviewService previewService;
    private final SitePreviewRefresher refresher;
    private final AppProperties properties;
    private final Clock clock;

    public MyShowcaseService(AdProfileRepository profileRepository, SitePreviewService previewService,
                             SitePreviewRefresher refresher, AppProperties properties, Clock clock) {
        this.profileRepository = profileRepository;
        this.previewService = previewService;
        this.refresher = refresher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyShowcase get(UUID userId) {
        AdProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            return new MyShowcase(ShowcaseStatus.NONE, null, null, null, null, null);
        }
        String url = profile.getWebsiteUrl();
        SitePreview preview = previewService.find(url).orElse(null);
        previewService.refreshIfStale(url, userId, preview);
        return toDto(url, preview);
    }

    /** Vuelve a leer su web en segundo plano. */
    @Transactional(readOnly = true)
    public MyShowcase refresh(UUID userId) {
        AdProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("AD_PROFILE_NOT_FOUND", "You haven't created your ad yet."));
        String url = profile.getWebsiteUrl();
        SitePreview preview = previewService.find(url).orElse(null);
        Instant canRefreshAt = canRefreshAt(preview);
        if (canRefreshAt != null && canRefreshAt.isAfter(clock.instant())) {
            long seconds = Math.max(1, Duration.between(clock.instant(), canRefreshAt).toSeconds());
            throw ApiException.conflict("SHOWCASE_REFRESH_TOO_SOON",
                    "We just read your website. You can ask again in " + seconds + " seconds.");
        }
        refresher.request(url, userId, Duration.ZERO);
        return toDto(url, preview);
    }

    private MyShowcase toDto(String url, SitePreview preview) {
        boolean reading = refresher.isReading(url);
        ShowcaseStatus status;
        if (reading || preview == null) {
            status = properties.previews().enabled() ? ShowcaseStatus.PENDING : ShowcaseStatus.FAILED;
        } else {
            status = preview.isReady() ? ShowcaseStatus.READY : ShowcaseStatus.FAILED;
        }
        return new MyShowcase(status,
                preview != null && preview.isReady() ? previewService.toShowcase(url, preview) : null,
                url,
                preview == null ? null : preview.getFetchedAt(),
                preview == null ? null : preview.getError(),
                canRefreshAt(preview));
    }

    private Instant canRefreshAt(SitePreview preview) {
        if (preview == null || preview.getFetchedAt() == null || preview.getSource() == SitePreview.Source.DEMO) {
            return null;
        }
        return preview.getFetchedAt().plus(properties.previews().refreshCooldown());
    }
}
