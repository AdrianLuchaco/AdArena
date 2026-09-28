-- =====================================================================
-- V9: Recuperación de contraseña y nuevos tipos de aviso
-- =====================================================================

-- Enlaces de "he olvidado mi contraseña". Como con los refresh tokens, solo se guarda el
-- SHA-256 del token: quien robara la base de datos no podría usarlos.
CREATE TABLE password_reset_tokens (
    id          uuid          PRIMARY KEY,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64)   NOT NULL,
    expires_at  timestamptz   NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, created_at DESC);

-- Avisos nuevos:
--   CANDIDATE_PROMOTED -> el ganador fue rechazado y ahora el candidato eres tú
--   TOP_UP_REJECTED    -> el admin descartó una recarga (p. ej. no llegó el dinero)
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'OUTBID', 'AUCTION_WON', 'AUCTION_LOST', 'AD_APPROVED', 'AD_REJECTED',
    'WINNER_REFUNDED', 'TOP_UP_SUCCEEDED', 'TOP_UP_REJECTED', 'CANDIDATE_PROMOTED'));

-- Pantalla de moderación: "candidatos pendientes cuya ventana ya terminó" (caducidad automática)
CREATE INDEX ix_ad_slots_pending_window ON ad_slots (ends_at) WHERE status = 'PENDING_REVIEW';
