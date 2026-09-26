package com.publifi.admin.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Registro inmutable de cada acción de administración. */
@Entity
@Immutable
@Table(name = "admin_audit_log")
public class AdminAuditLog extends BaseEntity {

    private UUID adminId;
    private String action;
    private String targetType;
    private String targetId;

    /** JSON libre con el detalle (p. ej. valores antes/después de un cambio de configuración). */
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @CreationTimestamp
    private Instant createdAt;

    protected AdminAuditLog() {
        // JPA
    }

    public AdminAuditLog(UUID adminId, String action, String targetType, String targetId, String detailsJson) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = detailsJson;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
