package com.adarena.image.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Imagen ya saneada por el servidor (re-codificada, sin metadatos). Inmutable.
 * <p>
 * Se guarda en PostgreSQL para no depender de otro servicio al principio; los logos pesan
 * poco (máx. 2 MB) y se sirven con caché HTTP larga. Si el tráfico crece, se puede mover a
 * un almacenamiento de objetos (Cloudflare R2 / S3) sin cambiar el resto del modelo.
 */
@Entity
@Immutable
@Table(name = "images")
public class StoredImage extends BaseEntity {

    private UUID ownerId;
    private String contentType;
    private byte[] data;
    private int sizeBytes;
    private int width;
    private int height;
    private String sha256;

    @CreationTimestamp
    private Instant createdAt;

    protected StoredImage() {
        // JPA
    }

    public StoredImage(UUID ownerId, String contentType, byte[] data, int width, int height, String sha256) {
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.width = width;
        this.height = height;
        this.sha256 = sha256;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
