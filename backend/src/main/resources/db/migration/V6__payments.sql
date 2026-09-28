-- =====================================================================
-- V6: Recargas del monedero (independientes del proveedor de pago)
-- =====================================================================
-- Método inicial: TRANSFERENCIA BANCARIA (gratis, importe libre).
--   1. El usuario pide recargar X € y recibe un código de referencia único (p. ej. PUB-7K3Q9D).
--   2. Hace una transferencia (idealmente inmediata) a tu IBAN con ese código en el concepto.
--   3. Tú (o un proceso que lee el extracto del banco) confirmas la recarga -> se abona el saldo.
-- El diseño admite añadir más adelante una pasarela con tarjeta (method = 'CARD') sin
-- cambiar el resto del modelo.

CREATE TABLE top_ups (
    id                     uuid          PRIMARY KEY,
    user_id                uuid          NOT NULL REFERENCES users (id),
    method                 varchar(20)   NOT NULL,
    status                 varchar(20)   NOT NULL,
    -- Importe que el usuario dijo que iba a enviar
    requested_cents        bigint        NOT NULL,
    -- Importe realmente recibido (puede diferir: se abona lo recibido)
    received_cents         bigint,
    currency               varchar(3)    NOT NULL DEFAULT 'eur',
    -- Código que el usuario pone en el concepto de la transferencia
    reference_code         varchar(20)   NOT NULL,
    -- Para pasarelas: nombre del proveedor y su id de pago; para transferencias, id del
    -- movimiento bancario si se importa el extracto.
    provider               varchar(30),
    provider_payment_id    varchar(255),
    -- Admin que confirmó la transferencia manualmente (NULL si fue automático)
    confirmed_by           uuid          REFERENCES users (id),
    ledger_transaction_id  uuid          REFERENCES ledger_transactions (id),
    expires_at             timestamptz   NOT NULL,
    completed_at           timestamptz,
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    version                bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_top_ups_reference        UNIQUE (reference_code),
    CONSTRAINT uq_top_ups_provider_payment UNIQUE (provider, provider_payment_id),
    CONSTRAINT ck_top_ups_method           CHECK (method IN ('BANK_TRANSFER', 'CARD')),
    CONSTRAINT ck_top_ups_status           CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_top_ups_amounts          CHECK (requested_cents > 0 AND (received_cents IS NULL OR received_cents > 0)),
    CONSTRAINT ck_top_ups_currency         CHECK (currency = 'eur'),
    CONSTRAINT ck_top_ups_succeeded        CHECK (status <> 'SUCCEEDED'
                                               OR (ledger_transaction_id IS NOT NULL
                                                   AND received_cents IS NOT NULL
                                                   AND completed_at IS NOT NULL))
);

CREATE INDEX ix_top_ups_user    ON top_ups (user_id, created_at DESC);
CREATE INDEX ix_top_ups_pending ON top_ups (created_at) WHERE status = 'PENDING';

-- Idempotencia de notificaciones automáticas de un proveedor (webhooks o movimientos
-- bancarios importados): el mismo (proveedor, id de evento) solo se procesa una vez.
CREATE TABLE payment_events (
    id            uuid           PRIMARY KEY,
    provider      varchar(30)    NOT NULL,
    event_id      varchar(255)   NOT NULL,
    type          varchar(100)   NOT NULL,
    payload       jsonb          NOT NULL,
    received_at   timestamptz    NOT NULL DEFAULT now(),
    processed_at  timestamptz,
    error         varchar(1000),

    CONSTRAINT uq_payment_events_provider_event UNIQUE (provider, event_id)
);
