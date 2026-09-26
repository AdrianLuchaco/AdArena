-- =====================================================================
-- V6: Recargas con Stripe y eventos de webhook
-- =====================================================================

CREATE TABLE top_ups (
    id                          uuid          PRIMARY KEY,
    user_id                     uuid          NOT NULL REFERENCES users (id),
    amount_cents                bigint        NOT NULL,
    currency                    varchar(3)    NOT NULL DEFAULT 'eur',
    status                      varchar(20)   NOT NULL,
    stripe_checkout_session_id  varchar(255),
    stripe_payment_intent_id    varchar(255),
    ledger_transaction_id       uuid          REFERENCES ledger_transactions (id),
    completed_at                timestamptz,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_top_ups_session        UNIQUE (stripe_checkout_session_id),
    CONSTRAINT uq_top_ups_payment_intent UNIQUE (stripe_payment_intent_id),
    CONSTRAINT ck_top_ups_amount         CHECK (amount_cents > 0),
    CONSTRAINT ck_top_ups_currency       CHECK (currency = 'eur'),
    CONSTRAINT ck_top_ups_status         CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_top_ups_succeeded      CHECK (status <> 'SUCCEEDED'
                                             OR (ledger_transaction_id IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE INDEX ix_top_ups_user ON top_ups (user_id, created_at DESC);

-- Idempotencia de webhooks: Stripe puede enviar el mismo evento varias veces.
-- La PK es el id del evento de Stripe (evt_...), así que el segundo INSERT falla
-- y sabemos que ya lo habíamos recibido.
CREATE TABLE stripe_events (
    id            varchar(255)   PRIMARY KEY,
    type          varchar(100)   NOT NULL,
    payload       jsonb          NOT NULL,
    received_at   timestamptz    NOT NULL DEFAULT now(),
    processed_at  timestamptz,
    error         varchar(1000)
);
