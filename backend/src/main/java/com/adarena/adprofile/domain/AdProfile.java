package com.adarena.adprofile.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Perfil de anuncio del usuario (uno por usuario). Obligatorio para pujar.
 * Al cerrar una subasta se copia una "foto" en la participación, así que editarlo después
 * no altera el anuncio que ya ganó.
 */
@Entity
@Table(name = "ad_profiles")
public class AdProfile extends BaseEntity {

    private UUID userId;
    private String companyName;
    private String websiteUrl;
    private String description;
    private UUID imageId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected AdProfile() {
        // JPA
    }

    public AdProfile(UUID userId, String companyName, String websiteUrl, String description, UUID imageId) {
        this.userId = userId;
        update(companyName, websiteUrl, description, imageId);
    }

    public void update(String companyName, String websiteUrl, String description, UUID imageId) {
        this.companyName = companyName;
        this.websiteUrl = websiteUrl;
        this.description = description;
        this.imageId = imageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getDescription() {
        return description;
    }

    public UUID getImageId() {
        return imageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
