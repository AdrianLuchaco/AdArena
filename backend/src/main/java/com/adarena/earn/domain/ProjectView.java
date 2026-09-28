package com.adarena.earn.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que un usuario ha visto de un proyecto en un día: cuántos "ticks" de 10 s le han contado,
 * cuántos puntos ha ganado y si ya se llevó el bonus de los 60 s. Una fila por (quien mira,
 * proyecto, día); el proyecto se identifica por su dueño, porque cada usuario tiene un solo anuncio.
 */
@Entity
@Table(name = "project_views")
public class ProjectView extends BaseEntity {

    private UUID viewerId;
    private UUID ownerId;
    private LocalDate viewDate;
    private Instant sessionStartedAt;
    private int ticks;
    private int pointsEarned;
    private boolean bonusAwarded;
    private Instant lastTickAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected ProjectView() {
        // JPA
    }

    public ProjectView(UUID viewerId, UUID ownerId, LocalDate viewDate, Instant now) {
        if (viewerId.equals(ownerId)) {
            throw new IllegalArgumentException("Nobody earns points viewing their own project");
        }
        this.viewerId = viewerId;
        this.ownerId = ownerId;
        this.viewDate = viewDate;
        this.sessionStartedAt = now;
    }

    /** Abre (o vuelve a abrir) la página del proyecto: el siguiente tick cuenta 10 s desde aquí. */
    public void startSession(Instant now) {
        this.sessionStartedAt = now;
    }

    /** Apunta {@code count} tramos de 10 s con los puntos que les correspondan (ya recortados al límite diario). */
    public void recordTicks(Instant now, int count, int points, boolean bonus) {
        this.ticks += count;
        this.pointsEarned += points;
        this.bonusAwarded = this.bonusAwarded || bonus;
        this.lastTickAt = now;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public LocalDate getViewDate() {
        return viewDate;
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }

    public int getTicks() {
        return ticks;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public boolean isBonusAwarded() {
        return bonusAwarded;
    }

    public Instant getLastTickAt() {
        return lastTickAt;
    }
}
