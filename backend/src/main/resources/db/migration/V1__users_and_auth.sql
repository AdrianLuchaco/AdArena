-- =====================================================================
-- V1: Usuarios y autenticación
-- =====================================================================
-- Los IDs son UUID generados por la aplicación (no adivinables en URLs).
-- Todas las fechas son timestamptz (se guardan en UTC).

CREATE TABLE users (
    id                      uuid          PRIMARY KEY,
    email                   varchar(254)  NOT NULL,
    password_hash           varchar(100)  NOT NULL,
    display_name            varchar(80)   NOT NULL,
    role                    varchar(20)   NOT NULL,
    enabled                 boolean       NOT NULL DEFAULT true,
    email_verified_at       timestamptz,
    -- Versión de los Términos aceptada (necesario para demostrar el consentimiento, RGPD)
    accepted_terms_version  varchar(20)   NOT NULL,
    accepted_terms_at       timestamptz   NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    version                 bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_email UNIQUE (email),
    -- La aplicación normaliza el email a minúsculas; la BD lo garantiza.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Refresh tokens: solo guardamos el hash SHA-256, nunca el token en claro.
-- family_id agrupa las rotaciones de un mismo login para detectar reutilización.
CREATE TABLE refresh_tokens (
    id          uuid          PRIMARY KEY,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64)   NOT NULL,
    family_id   uuid          NOT NULL,
    expires_at  timestamptz   NOT NULL,
    revoked_at  timestamptz,
    user_agent  varchar(255),
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
