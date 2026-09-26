-- =====================================================================
-- V8: Auditoría de acciones de administración y tabla de ShedLock
-- =====================================================================

-- Toda acción de admin (aprobar, rechazar, cambiar configuración, ajustes de saldo)
-- deja rastro: quién, qué, cuándo y sobre qué.
CREATE TABLE admin_audit_log (
    id           uuid          PRIMARY KEY,
    admin_id     uuid          NOT NULL REFERENCES users (id),
    action       varchar(50)   NOT NULL,
    target_type  varchar(40)   NOT NULL,
    target_id    varchar(100)  NOT NULL,
    details      jsonb,
    created_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_admin_audit_log_created ON admin_audit_log (created_at DESC);
CREATE INDEX ix_admin_audit_log_target  ON admin_audit_log (target_type, target_id);

-- Tabla estándar de ShedLock: impide que dos instancias del backend ejecuten
-- a la vez la misma tarea programada (p. ej. el cierre diario).
CREATE TABLE shedlock (
    name        varchar(64)   NOT NULL,
    lock_until  timestamp     NOT NULL,
    locked_at   timestamp     NOT NULL,
    locked_by   varchar(255)  NOT NULL,
    PRIMARY KEY (name)
);
