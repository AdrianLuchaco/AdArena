-- =====================================================================
-- V4: Huecos de anuncio (ganadores) y moderación
-- =====================================================================
-- Tras cerrar una subasta con pujas se crea un ad_slot PENDING_REVIEW para el 1.º.
-- Si el admin lo rechaza, ese slot pasa a REJECTED y se crea otro para el siguiente
-- clasificado (candidate_rank = 2, 3, ...). La ventana de emisión es fija: si el
-- admin tarda en aprobar, esas horas se muestra el contenido base.
--
-- Estados:
--   PENDING_REVIEW -> dinero retenido, esperando al admin
--   APPROVED       -> se cobra y se muestra en su ventana
--   REJECTED       -> se devuelve el 100 % al saldo del usuario
--   EXPIRED        -> terminó la ventana sin moderar: se devuelve el 100 %
CREATE TABLE ad_slots (
    id                uuid           PRIMARY KEY,
    auction_id        uuid           NOT NULL REFERENCES auctions (id),
    participation_id  uuid           NOT NULL REFERENCES auction_participations (id),
    user_id           uuid           NOT NULL REFERENCES users (id),
    candidate_rank    integer        NOT NULL,
    status            varchar(20)    NOT NULL,
    amount_cents      bigint         NOT NULL,
    starts_at         timestamptz    NOT NULL,
    ends_at           timestamptz    NOT NULL,
    reviewed_by       uuid           REFERENCES users (id),
    reviewed_at       timestamptz,
    rejection_reason  varchar(500),
    created_at        timestamptz    NOT NULL DEFAULT now(),
    updated_at        timestamptz    NOT NULL DEFAULT now(),
    version           bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_ad_slots_participation UNIQUE (participation_id),
    CONSTRAINT ck_ad_slots_status        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_ad_slots_rank          CHECK (candidate_rank >= 1),
    CONSTRAINT ck_ad_slots_amount        CHECK (amount_cents > 0),
    CONSTRAINT ck_ad_slots_window        CHECK (ends_at > starts_at),
    CONSTRAINT ck_ad_slots_rejection     CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- Por subasta, como mucho un candidato "vivo" (pendiente o aprobado) a la vez.
CREATE UNIQUE INDEX ux_ad_slots_one_live_per_auction
    ON ad_slots (auction_id) WHERE status IN ('PENDING_REVIEW', 'APPROVED');

-- Consulta de la portada: "¿qué anuncio aprobado está en ventana ahora?"
CREATE INDEX ix_ad_slots_approved_window
    ON ad_slots (starts_at, ends_at) WHERE status = 'APPROVED';
CREATE INDEX ix_ad_slots_pending ON ad_slots (created_at) WHERE status = 'PENDING_REVIEW';
