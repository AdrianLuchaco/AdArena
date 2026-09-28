package com.adarena.image.dto;

import com.adarena.image.domain.StoredImage;
import com.adarena.image.service.ImageService;

import java.util.UUID;

public record ImageResponse(
        UUID id,
        String url,
        String contentType,
        int width,
        int height,
        int sizeBytes
) {

    public static ImageResponse from(StoredImage image) {
        return new ImageResponse(image.getId(), ImageService.publicUrl(image.getId()), image.getContentType(),
                image.getWidth(), image.getHeight(), image.getSizeBytes());
    }
}
