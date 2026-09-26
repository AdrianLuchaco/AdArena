-- =====================================================================
-- V7: Notificaciones in-app y bandeja de salida de emails (outbox)
-- =====================================================================

CREATE TABLE notifications (
    id          uuid           PRIMARY KEY,
    user_id     uuid           NOT NULL REFERENCES users (id),
    type        varchar(30)    NOT NULL,
    title       varchar(150)   NOT NULL,
    body        varchar(1000)  NOT NULL,
    link        varchar(500),
    read_at     timestamptz,
    created_at  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT ck_notifications_type CHECK (type IN (
        'OUTBID', 'AUCTION_WON', 'AUCTION_LOST', 'AD_APPROVED', 'AD_REJECTED',
        'WINNER_REFUNDED', 'TOP_UP_SUCCEEDED'))
);

CREATE INDEX ix_notifications_user        ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Patrón "outbox": el email se guarda en la MISMA transacción que la puja.
-- Un proceso aparte lo envía después. Si la puja se deshace, el email tampoco sale;
-- si el SMTP falla, se reintenta sin perder nada.
CREATE TABLE email_outbox (
    id               uuid           PRIMARY KEY,
    to_email         varchar(254)   NOT NULL,
    subject          varchar(200)   NOT NULL,
    html_body        text           NOT NULL,
    text_body        text           NOT NULL,
    status           varchar(20)    NOT NULL DEFAULT 'PENDING',
    attempts         integer        NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz    NOT NULL DEFAULT now(),
    last_error       varchar(1000),
    -- Evita duplicados y spam (p. ej. un solo "te han superado" por usuario y ventana de tiempo)
    dedup_key        varchar(150),
    created_at       timestamptz    NOT NULL DEFAULT now(),
    sent_at          timestamptz,

    CONSTRAINT uq_email_outbox_dedup  UNIQUE (dedup_key),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_email_outbox_sent   CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX ix_email_outbox_pending ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
