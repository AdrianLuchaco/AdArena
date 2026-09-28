package com.adarena.earn.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Un enlace que un usuario promociona (su canal de YouTube, su perfil de X, su web…). Los demás
 * lo ven en Créditos extra y ganan puntos por VISITARLO. Nunca se premia dar like o seguir: lo
 * prohíben YouTube y X y podría costarle el canal a quien se promociona.
 * <p>
 * {@code @DynamicUpdate}: al guardar solo se escriben las columnas que han cambiado. Los contadores
 * (completions, reports) se suman con consultas directas; así un cambio de estado nunca pisa un
 * contador que otro usuario acaba de incrementar.
 */
@Entity
@Table(name = "social_tasks")
@DynamicUpdate
public class SocialTask extends BaseEntity {

    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    private SocialPlatform platform;

    private String title;
    private String description;
    private String url;

    @Enumerated(EnumType.STRING)
    private SocialTaskStatus status;

    private int rewardPoints;
    private boolean featured;
    private int reports;
    private int completions;
    private String hiddenReason;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected SocialTask() {
        // JPA
    }

    public SocialTask(UUID ownerId, String title, String description, String url, int rewardPoints) {
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.url = url;
        this.platform = SocialPlatform.fromUrl(url);
        this.rewardPoints = rewardPoints;
        this.status = SocialTaskStatus.ACTIVE;
    }

    public void pause() {
        if (status != SocialTaskStatus.ACTIVE) {
            throw new IllegalStateException("Only an active task can be paused");
        }
        status = SocialTaskStatus.PAUSED;
    }

    public void resume() {
        if (status != SocialTaskStatus.PAUSED) {
            throw new IllegalStateException("Only a paused task can be resumed");
        }
        status = SocialTaskStatus.ACTIVE;
    }

    public void hide(String reason) {
        status = SocialTaskStatus.HIDDEN;
        hiddenReason = reason;
    }

    /** El admin la vuelve a publicar: las denuncias anteriores dejan de contar. */
    public void restore() {
        status = SocialTaskStatus.ACTIVE;
        hiddenReason = null;
        reports = 0;
    }

    /** Destacado por el admin: sale el primero en Bonus links y da {@code points} puntos. */
    public void feature(int points) {
        featured = true;
        rewardPoints = points;
    }

    /** Vuelve a ser un enlace normal, con los puntos normales. */
    public void unfeature(int points) {
        featured = false;
        rewardPoints = points;
    }

    public boolean isActive() {
        return status == SocialTaskStatus.ACTIVE;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public SocialPlatform getPlatform() {
        return platform;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public SocialTaskStatus getStatus() {
        return status;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public boolean isFeatured() {
        return featured;
    }

    public int getReports() {
        return reports;
    }

    public int getCompletions() {
        return completions;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
