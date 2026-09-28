package com.adarena.earn.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un usuario hace una tarea un día concreto: {@code startedAt} lo pone el servidor al abrir el
 * enlace y la recompensa solo se da si ha pasado el tiempo mínimo desde entonces.
 */
@Entity
@Table(name = "social_task_completions")
public class SocialTaskCompletion extends BaseEntity {

    private UUID taskId;
    private UUID userId;
    private LocalDate taskDate;
    private Instant startedAt;
    private Instant completedAt;
    private Integer pointsAwarded;

    protected SocialTaskCompletion() {
        // JPA
    }

    public SocialTaskCompletion(UUID taskId, UUID userId, LocalDate taskDate, Instant startedAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.taskDate = taskDate;
        this.startedAt = startedAt;
    }

    /** Vuelve a abrir el enlace sin haberla completado: el tiempo mínimo cuenta desde ahora. */
    public void restart(Instant now) {
        if (isCompleted()) {
            throw new IllegalStateException("Task already completed today");
        }
        this.startedAt = now;
    }

    public void complete(Instant now, int points) {
        if (isCompleted()) {
            throw new IllegalStateException("Task already completed today");
        }
        this.completedAt = now;
        this.pointsAwarded = points;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getTaskDate() {
        return taskDate;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getPointsAwarded() {
        return pointsAwarded;
    }
}
