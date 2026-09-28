package com.adarena.image.repository;

import com.adarena.image.domain.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {

    /** Comprueba la propiedad sin cargar los bytes de la imagen. */
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
