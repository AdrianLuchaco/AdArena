package com.adarena.adprofile.dto;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.image.service.ImageService;

import java.time.Instant;
import java.util.UUID;

public record AdProfileResponse(
        String companyName,
        String websiteUrl,
        String description,
        UUID imageId,
        String imageUrl,
        Instant updatedAt
) {

    public static AdProfileResponse from(AdProfile profile) {
        return new AdProfileResponse(profile.getCompanyName(), profile.getWebsiteUrl(), profile.getDescription(),
                profile.getImageId(), ImageService.publicUrl(profile.getImageId()), profile.getUpdatedAt());
    }
}
