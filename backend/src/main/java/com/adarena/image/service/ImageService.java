package com.adarena.image.service;

import com.adarena.common.error.ApiException;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.repository.StoredImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ImageService {

    public static final String PUBLIC_PATH = "/api/public/images/";

    private final StoredImageRepository repository;
    private final ImageProcessor processor;

    public ImageService(StoredImageRepository repository, ImageProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    /** Sanea y guarda una imagen subida por un usuario. */
    @Transactional
    public StoredImage store(UUID ownerId, byte[] upload) {
        return save(ownerId, processor.process(upload));
    }

    /** Guarda una imagen generada por el propio servidor (p. ej. los datos de demostración). */
    @Transactional
    public StoredImage store(UUID ownerId, BufferedImage image) {
        return save(ownerId, processor.encode(image));
    }

    /** Guarda una imagen que ya pasó por {@link ImageProcessor} (p. ej. las leídas de la web de un proyecto). */
    @Transactional
    public StoredImage store(UUID ownerId, ProcessedImage image) {
        return save(ownerId, image);
    }

    @Transactional(readOnly = true)
    public StoredImage get(UUID imageId) {
        return repository.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "That image doesn't exist."));
    }

    public static String publicUrl(UUID imageId) {
        return imageId == null ? null : PUBLIC_PATH + imageId;
    }

    private StoredImage save(UUID ownerId, ProcessedImage image) {
        return repository.save(new StoredImage(ownerId, image.contentType(), image.data(),
                image.width(), image.height(), sha256(image.data())));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
