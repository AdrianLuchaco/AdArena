package com.adarena.adprofile.service;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.dto.AdProfileRequest;
import com.adarena.adprofile.dto.AdProfileResponse;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.common.error.ApiException;
import com.adarena.common.error.FieldValidationException;
import com.adarena.common.text.TextSanitizer;
import com.adarena.common.text.WebsiteUrlSanitizer;
import com.adarena.image.repository.StoredImageRepository;
import com.adarena.site.service.SitePreviewRefresher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Perfil de anuncio del usuario (regla 3). Se puede editar siempre: al cerrar cada subasta se
 * guarda una copia, así que los cambios nunca alteran un anuncio que ya ganó.
 */
@Service
public class AdProfileService {

    private static final int MIN_COMPANY_LENGTH = 2;
    private static final int MIN_DESCRIPTION_LENGTH = 10;

    private final AdProfileRepository profileRepository;
    private final StoredImageRepository imageRepository;
    private final SitePreviewRefresher sitePreviewRefresher;

    public AdProfileService(AdProfileRepository profileRepository, StoredImageRepository imageRepository,
                            SitePreviewRefresher sitePreviewRefresher) {
        this.profileRepository = profileRepository;
        this.imageRepository = imageRepository;
        this.sitePreviewRefresher = sitePreviewRefresher;
    }

    @Transactional(readOnly = true)
    public AdProfileResponse get(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(AdProfileResponse::from)
                .orElseThrow(() -> ApiException.notFound("AD_PROFILE_NOT_FOUND", "You haven't created your ad yet."));
    }

    /** Crea el perfil si no existe o lo actualiza si ya existe. */
    @Transactional
    public AdProfileResponse save(UUID userId, AdProfileRequest request) {
        String companyName = TextSanitizer.singleLine(request.companyName());
        if (companyName.length() < MIN_COMPANY_LENGTH) {
            throw new FieldValidationException("companyName", "The company name is too short.");
        }
        String description = TextSanitizer.multiLine(request.description());
        if (description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new FieldValidationException("description",
                    "Write a description of at least 10 characters.");
        }
        String websiteUrl;
        try {
            websiteUrl = WebsiteUrlSanitizer.sanitize(request.websiteUrl());
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("websiteUrl", e.getMessage());
        }
        if (!imageRepository.existsByIdAndOwnerId(request.imageId(), userId)) {
            throw new FieldValidationException("imageId", "The image doesn't exist or isn't yours. Upload it again.");
        }

        AdProfile profile = profileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.update(companyName, websiteUrl, description, request.imageId());
                    return existing;
                })
                .orElseGet(() -> new AdProfile(userId, companyName, websiteUrl, description, request.imageId()));
        AdProfileResponse saved = AdProfileResponse.from(profileRepository.saveAndFlush(profile));
        // Leemos su web en segundo plano: para el visor de "Gana puntos" y para su presentación si gana
        sitePreviewRefresher.request(websiteUrl, userId);
        return saved;
    }
}
