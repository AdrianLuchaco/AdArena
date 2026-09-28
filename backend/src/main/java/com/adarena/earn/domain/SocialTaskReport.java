package com.adarena.earn.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Denuncia de un usuario contra una promoción (una por usuario y tarea). */
@Entity
@Immutable
@Table(name = "social_task_reports")
public class SocialTaskReport extends BaseEntity {

    private UUID taskId;
    private UUID userId;
    private String reason;

    @CreationTimestamp
    private Instant createdAt;

    protected SocialTaskReport() {
        // JPA
    }

    public SocialTaskReport(UUID taskId, UUID userId, String reason) {
        this.taskId = taskId;
        this.userId = userId;
        this.reason = reason;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
