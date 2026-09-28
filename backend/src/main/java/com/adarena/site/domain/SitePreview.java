package com.adarena.site.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que AdArena sabe de una web: si se puede mostrar dentro de AdArena y lo que hemos leído de
 * ella (nombre, titular, descripción, color, logo, fotos y frases destacadas). Una fila por
 * dirección, compartida por quien la use (anuncio o promoción).
 * <p>
 * La galería y las frases se guardan como JSON (texto); {@code SitePreviewService} las convierte.
 */
@Entity
@Table(name = "site_previews")
public class SitePreview extends BaseEntity {

    public enum Status { READY, FAILED }

    public enum Source { WEB, DEMO }

    private String url;
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String finalUrl;
    private boolean frameable;
    private String embedUrl;
    private String siteName;
    private String title;
    private String description;
    private String themeColor;
    private UUID iconImageId;
    private UUID heroImageId;
    private String iconSrc;
    private String heroSrc;

    @JdbcTypeCode(SqlTypes.JSON)
    private String gallery;

    @JdbcTypeCode(SqlTypes.JSON)
    private String highlights;

    private String error;
    private Instant fetchedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected SitePreview() {
        // JPA
    }

    public SitePreview(String url, UUID ownerId, Source source) {
        this.url = url;
        this.ownerId = ownerId;
        this.source = source;
        this.status = Status.FAILED;
        this.gallery = "[]";
        this.highlights = "[]";
    }

    /** Resultado de leer la web (o de los datos de ejemplo). */
    public void update(Content content, Instant now) {
        this.status = Status.READY;
        this.finalUrl = content.finalUrl();
        this.frameable = content.frameable();
        this.embedUrl = content.embedUrl();
        this.siteName = content.siteName();
        this.title = content.title();
        this.description = content.description();
        this.themeColor = content.themeColor();
        this.iconImageId = content.iconImageId();
        this.iconSrc = content.iconSrc();
        this.heroImageId = content.heroImageId();
        this.heroSrc = content.heroSrc();
        this.gallery = content.galleryJson();
        this.highlights = content.highlightsJson();
        this.error = null;
        this.fetchedAt = now;
    }

    /**
     * No se ha podido leer. Si ya teníamos datos buenos de antes, se conservan (una web caída un
     * rato no debe dejar al ganador sin presentación); solo se apunta el error y la hora.
     */
    public void markFailed(String error, Instant now) {
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 300));
        this.fetchedAt = now;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    /**
     * Todo lo que se guarda tras leer una web.
     *
     * @param galleryJson    [{"src": "...", "imageId": "..."}]
     * @param highlightsJson ["...", "..."]
     */
    public record Content(String finalUrl, boolean frameable, String embedUrl, String siteName, String title,
                          String description, String themeColor, UUID iconImageId, String iconSrc,
                          UUID heroImageId, String heroSrc, String galleryJson, String highlightsJson) {
    }

    public String getUrl() {
        return url;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Source getSource() {
        return source;
    }

    public Status getStatus() {
        return status;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public boolean isFrameable() {
        return frameable;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getThemeColor() {
        return themeColor;
    }

    public UUID getIconImageId() {
        return iconImageId;
    }

    public UUID getHeroImageId() {
        return heroImageId;
    }

    public String getIconSrc() {
        return iconSrc;
    }

    public String getHeroSrc() {
        return heroSrc;
    }

    public String getGallery() {
        return gallery;
    }

    public String getHighlights() {
        return highlights;
    }

    public String getError() {
        return error;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
